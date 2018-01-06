package reactivelite.operators

import reactivelite.*

internal class MapOperator<in I, out O>(
        private val onStart: OnTaskStart<in I>,
        private val mapper: Func1<in I, out O>)
    : OnTaskStart<O> {

    override fun invoke(consumer: TaskConsumer<O>): Cancellable {
        val taskConsumer = object : TaskConsumer<I> {
            override fun onSuccess(event: I) {
                try {
                    val s = mapper.invoke(event)
                    consumer.onSuccess(s)
                } catch (throwable: Throwable) {
                    throwIfFatal(throwable)
                    consumer.onError(throwable)
                }

            }

            override fun onError(t: Throwable) {
                consumer.onError(t)
            }
        }
        return onStart.invoke(taskConsumer)
    }
}
