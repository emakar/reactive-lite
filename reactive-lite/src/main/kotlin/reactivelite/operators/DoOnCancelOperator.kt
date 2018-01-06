package reactivelite.operators

import reactivelite.*

internal class DoOnCancelOperator<out T>(private val onStart: OnTaskStart<in T>,
                                         private val onCancel: Action0) : OnTaskStart<T> {

    override fun invoke(consumer: TaskConsumer<T>): Cancellable {
        val downstreamCancellable = onStart.invoke(consumer)
        return {
            onCancel.invoke()
            downstreamCancellable.cancel()
        }
    }
}