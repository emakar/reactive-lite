package reactivelite.bus

import reactivelite.Cancellable
import reactivelite.CompletableBusConsumer
import reactivelite.Func1
import reactivelite.OnListen

internal class FilterOnListen<T>(private val upstream: OnListen<T>,
                                 private val predicate: Func1<in T, Boolean>) : OnListen<T> {

    override fun invoke(downstream: CompletableBusConsumer<T>): Cancellable {
        return upstream.invoke(object : CompletableBusConsumer<T> {
            override fun onError(throwable: Throwable) {
                downstream.onError(throwable)
            }

            override fun onComplete() {
                downstream.onComplete()
            }

            override fun onEvent(event: T) {
                if (predicate.invoke(event)) {
                    downstream.onEvent(event)
                }
            }
        })
    }
}