package reactivelite.bus

import reactivelite.Cancellable
import reactivelite.CompletableBusConsumer
import reactivelite.Func1
import reactivelite.OnListen

internal class MapOnListen<in T, out U>(private val upstream: OnListen<T>,
                                        private val mapper: Func1<in T, out U>) : OnListen<U> {

    override fun invoke(downstream: CompletableBusConsumer<U>): Cancellable {
        return upstream.invoke(object : CompletableBusConsumer<T> {
            override fun onEvent(event: T) {
                downstream.onEvent(mapper.invoke(event))
            }

            override fun onError(throwable: Throwable) {
                downstream.onError(throwable)
            }

            override fun onComplete() {
                downstream.onComplete()
            }
        })
    }
}
