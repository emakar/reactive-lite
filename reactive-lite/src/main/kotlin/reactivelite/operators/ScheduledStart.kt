package reactivelite.operators

import reactivelite.*
import java.util.concurrent.atomic.AtomicReference

internal class ScheduledStart<out T>(
        private val onStart: OnTaskStartAsync<T>,
        private val scheduler: Scheduler)
    : OnTaskStart<T> {

    override fun invoke(consumer: TaskConsumer<T>): Cancellable {
        return ScheduleArbiter(onStart, scheduler).start(consumer)
    }

    private class ScheduleArbiter<T> internal constructor(private val onStart: OnTaskStartAsync<T>,
                                                          private val scheduler: Scheduler) : Cancellable, TaskAsyncConsumer<T> {

        @Volatile private var cancelled: Boolean = false
        @Volatile private var schedulerCancellable = nonCancellable()
        @Volatile private var clientCancellable = nonCancellable()
        private val downstream = AtomicReference<TaskConsumer<T>>()

        fun start(downstream: TaskConsumer<T>): Cancellable {
            this.downstream.set(downstream)
            schedulerCancellable = scheduler.schedule {
                try {
                    onStart.invoke(this)
                } catch (t: Throwable) {
                    try {
                        throwIfFatal(t)
                        downstream.onError(t)
                    } catch (onErrorThrowable: Throwable) {
                        FallbackErrorHandler.handle(MergedException(t, onErrorThrowable))
                    }

                }
            }
            return this
        }

        override fun onSuccess(event: T) {
            val downstream = this.downstream.getAndSet(null)
            downstream?.onSuccess(event)
        }

        override fun onError(t: Throwable) {
            val downstream = this.downstream.getAndSet(null)
            downstream?.onError(t)
        }

        override fun setOnCancelled(cancellable: Cancellable) {
            if (cancelled) {
                cancellable.cancel()
                return
            }

            synchronized(this) {
                if (cancelled) {
                    cancellable.cancel()
                } else {
                    clientCancellable = cancellable
                }
            }
        }

        override fun invoke() {
            if (!cancelled) {
                synchronized(this) {
                    if (!cancelled) {
                        cancelled = true
                        schedulerCancellable.cancel()
                        clientCancellable.cancel()
                    }
                }
            }
        }
    }
}
