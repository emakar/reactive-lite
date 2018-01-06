package reactivelite.operators

import reactivelite.*

internal class DoOnTerminateOperator<T>(private val onStart: OnTaskStart<in T>,
                                        private val onSuccess: Action1<in T>,
                                        private val onError: Action1<Throwable>) : OnTaskStart<T> {

    override fun invoke(consumer: TaskConsumer<T>): Cancellable {
        val taskConsumer = object : TaskConsumer<T> {
            override fun onSuccess(event: T) {
                try {
                    onSuccess.invoke(event)
                } catch (throwable: Throwable) {
                    throwIfFatal(throwable)
                    consumer.onError(throwable)
                    return
                }

                consumer.onSuccess(event)
            }

            override fun onError(t: Throwable) {
                try {
                    onError.invoke(t)
                } catch (throwable: Throwable) {
                    throwIfFatal(throwable)
                    consumer.onError(MergedException(t, throwable))
                    return
                }

                consumer.onError(t)
            }
        }
        return onStart.invoke(taskConsumer)
    }
}