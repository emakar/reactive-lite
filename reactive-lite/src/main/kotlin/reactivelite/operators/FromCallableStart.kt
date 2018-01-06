package reactivelite.operators

import reactivelite.*

internal class FromCallableStart<out T>(private val callable: Callable<T>) : OnTaskStartAsync<T> {

    override fun invoke(consumer: TaskAsyncConsumer<T>) {
        val cancellable = StatefulCancellable()
        consumer.setOnCancelled(cancellable)

        if (cancellable.isCancelled) {
            return
        }

        val t: T?
        try {
            t = callable.invoke()
        } catch (ex: Throwable) {
            throwIfFatal(ex)
            consumer.onError(ex)
            return
        }

        if (t != null) {
            consumer.onSuccess(t)
        } else {
            consumer.onError(NullPointerException("callable result must not be null"))
        }
    }
}
