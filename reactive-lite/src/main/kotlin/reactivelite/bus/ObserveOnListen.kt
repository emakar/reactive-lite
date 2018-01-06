package reactivelite.bus

import reactivelite.*
import java.util.*

internal class ObserveOnListen<out T>(private val upstream: OnListen<T>, private val scheduler: Scheduler) : OnListen<T> {

    override fun invoke(downstream: CompletableBusConsumer<T>): Cancellable {
        val consumer = ObserveOnConsumer(scheduler, SafeBusConsumer(downstream))
        return consumer.start(upstream)
    }

    private class ObserveOnConsumer<in T> internal constructor(private val scheduler: Scheduler,
                                                               private val downstream: CompletableBusConsumer<T>)
        : CompletableBusConsumer<T>, Cancellable {

        private val actions = ArrayDeque<Action0>()
        private val lock = Any()

        private var isScheduled: Boolean = false
        private var currentCancellable = nonCancellable()
        private var upstreamCancellable = nonCancellable()
        private var done: Boolean = false

        override fun onEvent(event: T) {
            synchronized(lock) {
                if (done) {
                    return
                }

                actions.add { trySendEvent(event) }
                schedule()
            }
        }

        private fun trySendEvent(t: T) {
            try {
                downstream.onEvent(t)
            } catch (throwable: Throwable) {
                synchronized(lock) {
                    done = true
                    dropActions()
                    handleErrorSafely(throwable)
                }
            }
        }

        override fun onError(throwable: Throwable) {
            synchronized(lock) {
                actions.add { handleErrorSafely(throwable) }
                schedule()
            }
        }

        override fun onComplete() {
            synchronized(lock) {
                actions.add {
                    try {
                        downstream.onComplete()
                    } catch (throwable: Throwable) {
                        handleErrorSafely(throwable)
                    }
                }
                schedule()
            }
        }

        private fun handleErrorSafely(throwable: Throwable) {
            try {
                downstream.onError(throwable)
            } catch (onErrorThrowable: Throwable) {
                FallbackErrorHandler.handle(MergedException(throwable, onErrorThrowable))
            }

        }

        internal fun dropActions() {
            synchronized(lock) {
                actions.clear()
                currentCancellable.cancel()
                currentCancellable = nonCancellable()
                isScheduled = false
            }
        }

        private fun schedule() {
            if (!isScheduled) {
                isScheduled = true
                currentCancellable = scheduler.schedule { runActions() }
            }
        }

        private fun runActions() {
            while (true) {
                var action: Action0? = null
                synchronized(lock) {
                    if (actions.isEmpty()) {
                        isScheduled = false
                        return
                    }
                    action = actions.poll()
                }
                action?.invoke()
            }
        }

        fun start(upstream: OnListen<T>): Cancellable {
            upstreamCancellable = upstream.invoke(this)
            return this
        }

        override fun invoke() {
            upstreamCancellable.cancel()
            dropActions()
        }
    }
}
