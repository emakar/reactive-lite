package reactivelite.bus

import reactivelite.*

internal class BusDispatcherImpl<T> : BusDispatcher<T> {

    private val bus: CommonBusDispatcher<T>

    override val value: T
        get() = bus.value()

    private constructor(bus: CommonBusDispatcher<T>) : super(bus.upstream) {
        this.bus = bus
    }

    internal constructor(latestValue: T? = null) : this(CommonBusDispatcher(latestValue))

    constructor(onListen: OnListen<T>) : super(onListen) {
        this.bus = CommonBusDispatcher(onListen)
    }

    override fun onEvent(event: T) {
        bus.onEvent(event)
    }

    override fun listen(consumer: BusConsumer<T>): Cancellable {
        return bus.listen(consumer)
    }

    override fun listen(consumer: Action1<T>): Cancellable {
        return bus.listen(consumer)
    }

    override fun hasValue(): Boolean {
        return bus.hasValue()
    }
}

internal class CompletableBusDispatcherImpl<T> : CompletableBusDispatcher<T> {

    private val bus: CommonBusDispatcher<T>

    private constructor(bus: CommonBusDispatcher<T>) : super(bus.upstream) {
        this.bus = bus
    }

    internal constructor(latestValue: T? = null) : this(CommonBusDispatcher(latestValue))

    constructor(onListen: OnListen<T>) : super(onListen) {
        this.bus = CommonBusDispatcher(onListen)
    }

    override fun onEvent(event: T) {
        bus.onEvent(event)
    }

    override fun onError(throwable: Throwable) {
        bus.onError(throwable)
    }

    override fun onComplete() {
        bus.onComplete()
    }

    override fun listen(consumer: CompletableBusConsumer<T>): Cancellable {
        return bus.listen(consumer)
    }

    override fun listen(onEvent: Action1<T>, onError: Action1<Throwable>): Cancellable {
        return bus.listen(onEvent, onError)
    }

    override fun listen(onEvent: Action1<T>, onError: Action1<Throwable>, onComplete: Action0): Cancellable {
        return bus.listen(onEvent, onError, onComplete)
    }

    override fun hasValue(): Boolean {
        return bus.hasValue()
    }

    override fun value(): T {
        return bus.value()
    }
}
