package reactivelite.operators

import reactivelite.*

internal class ObserveOnOperator<out T>(
        private val onStart: OnTaskStart<in T>,
        private val scheduler: Scheduler)
    : OnTaskStart<T> {

    override fun invoke(consumer: TaskConsumer<T>): Cancellable {
        return ObserveOnEmitter(onStart, scheduler, consumer).start()
    }

    private class ObserveOnEmitter<in T> internal constructor(private val onStart: OnTaskStart<in T>,
                                                              private val scheduler: Scheduler,
                                                              private val downstream: TaskConsumer<T>) : Cancellable, TaskConsumer<T> {

        private var schedulerCancellable = nonCancellable()
        @Volatile private var cancelled: Boolean = false

        fun start(): Cancellable {
            onStart.invoke(this)
            return this
        }

        @Synchronized override fun invoke() {
            cancelled = true
            schedulerCancellable.cancel()
        }

        override fun onSuccess(event: T) {
            if (!cancelled) {
                synchronized(this) {
                    if (!cancelled) {
                        schedulerCancellable = scheduler.schedule {
                            try {
                                downstream.onSuccess(event)
                            } catch (throwable: Throwable) {
                                handleErrorSafely(throwable)
                            }
                        }
                    }
                }
            }
        }

        override fun onError(t: Throwable) {
            if (!cancelled) {
                synchronized(this) {
                    if (!cancelled) {
                        schedulerCancellable = scheduler.schedule { handleErrorSafely(t) }
                    }
                }
            }
        }

        private fun handleErrorSafely(throwable: Throwable) {
            try {
                throwIfFatal(throwable)
                downstream.onError(throwable)
            } catch (onErrorThrowable: Throwable) {
                FallbackErrorHandler.handle(MergedException(throwable, onErrorThrowable))
            }
        }
    }
}
