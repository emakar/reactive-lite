package reactivelite.bus

import reactivelite.Action1
import reactivelite.Cancellable
import reactivelite.CompletableBusConsumer
import reactivelite.OnListen

internal class DoOnEventOnListen<T>(private val upstream: OnListen<in T>, private val action: Action1<in T>): OnListen<T> {

    override fun invoke(downstream: CompletableBusConsumer<T>): Cancellable {
        return upstream.invoke(object : CompletableBusConsumer<T> {
            override fun onError(throwable: Throwable) {
                downstream.onError(throwable)
            }

            override fun onComplete() {
                downstream.onComplete()
            }

            override fun onEvent(event: T) {
                action.invoke(event)
                downstream.onEvent(event)
            }
        })
    }
}