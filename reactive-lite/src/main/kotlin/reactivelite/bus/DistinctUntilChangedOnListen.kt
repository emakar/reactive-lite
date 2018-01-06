package reactivelite.bus

import reactivelite.*

internal class DistinctUntilChangedOnListen<T, U> : OnListen<T> {

    private val upstream: OnListen<T>
    private val keySelector: Func1<in T, out U>
    private val comparator: Func2<in U, in U, Boolean>

    @Suppress("UNCHECKED_CAST")
    constructor(upstream: OnListen<T>) {
        this.upstream = upstream
        this.keySelector = DEFAULT_KEY_SELECTOR as Func1<in T, out U>
        this.comparator = DEFAULT_COMPARATOR as Func2<in U, in U, Boolean>
    }

    @Suppress("UNCHECKED_CAST")
    constructor(upstream: OnListen<T>, keySelector: Func1<in T, out U>) {
        this.upstream = upstream
        this.keySelector = keySelector
        this.comparator = DEFAULT_COMPARATOR as Func2<in U, in U, Boolean>
    }

    @Suppress("UNCHECKED_CAST")
    constructor(upstream: OnListen<T>, comparator: Func2<in U, in U, Boolean>) {
        this.upstream = upstream
        this.keySelector = DEFAULT_KEY_SELECTOR as Func1<in T, out U>
        this.comparator = comparator
    }

    override fun invoke(downstream: CompletableBusConsumer<T>): Cancellable {
        return upstream.invoke(object : CompletableBusConsumer<T> {

            private var previous: U? = null

            override fun onError(throwable: Throwable) {
                downstream.onError(throwable)
            }

            override fun onComplete() {
                downstream.onComplete()
            }

            override fun onEvent(event: T) {
                val current = keySelector.invoke(event)
                if (previous == null || !comparator.invoke(previous!!, current)) {
                    previous = current
                    downstream.onEvent(event)
                } else {
                    previous = current
                }
            }
        })
    }

    companion object {

        private val DEFAULT_KEY_SELECTOR: Func1<*, *> = { t -> t }
        private val DEFAULT_COMPARATOR: Func2<*, *, Boolean> = { p1, p2 -> p1 == p2 }
    }
}
