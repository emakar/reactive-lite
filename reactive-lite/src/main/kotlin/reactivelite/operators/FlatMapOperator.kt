package reactivelite.operators

import reactivelite.*

internal class FlatMapOperator<in I, out O>(private val firstTask: Task<out I>,
                                            private val taskMapper: Func1<in I, out Task<out O>>)
    : OnTaskStart<O> {

    override fun invoke(consumer: TaskConsumer<O>): Cancellable {
        return FlatMapEmitter(consumer).start()
    }

    private inner class FlatMapEmitter internal constructor(private val downstream: TaskConsumer<O>) : Cancellable {
        private var currentTaskCancellable = nonCancellable()
        private var cancelled = false

        @Synchronized fun start(): Cancellable {
            return try {
                val cancellable = firstTask.start({ this.startNextTask(it) }, { downstream.onError(it) })

                // this::startNextTask could be executed synchronously,
                // which means firstTask is done (thus no need to cancel it),
                // whereas secondTask has to be cancelled
                if (currentTaskCancellable === nonCancellable()) {
                    currentTaskCancellable = cancellable
                }
                this
            } catch (throwable: Throwable) {
                throwIfFatal(throwable)
                downstream.onError(throwable)
                nonCancellable()
            }
        }

        @Synchronized fun startNextTask(i: I) {
            if (cancelled) {
                return
            }
            try {
                val secondTask = taskMapper.invoke(i)
                currentTaskCancellable = secondTask.start({ downstream.onSuccess(it) }, { downstream.onError(it) })
            } catch (throwable: Throwable) {
                throwIfFatal(throwable)
                downstream.onError(throwable)
            }
        }

        @Synchronized override fun invoke() {
            try {
                currentTaskCancellable.cancel()
                cancelled = true
            } catch (throwable: Throwable) {
                throwIfFatal(throwable)
                downstream.onError(throwable)
            }
        }
    }
}
