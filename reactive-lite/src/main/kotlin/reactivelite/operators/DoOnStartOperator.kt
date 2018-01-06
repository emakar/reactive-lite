package reactivelite.operators

import reactivelite.Action0
import reactivelite.Cancellable
import reactivelite.OnTaskStart
import reactivelite.TaskConsumer

internal class DoOnStartOperator<out T>(private val onStart: OnTaskStart<in T>,
                                        private val doOnStart: Action0) : OnTaskStart<T> {

    override fun invoke(consumer: TaskConsumer<T>): Cancellable {
        doOnStart.invoke()
        return onStart.invoke(consumer)
    }
}
