@file:Suppress("unused") // public api

package reactivelite

import reactivelite.bus.*


fun <T> Bus<T>.filter(predicate: Func1<in T, Boolean>): Bus<T> {
    return Bus.createUnsafe(FilterOnListen(upstream, predicate))
}
fun <T> CompletableBus<T>.filter(predicate: Func1<in T, Boolean>): CompletableBus<T> {
    return CompletableBus.create(FilterOnListen(upstream, predicate))
}

fun <U, T> Bus<T>.map(mapper: Func1<in T, out U>): Bus<U> {
    return Bus.createUnsafe(MapOnListen(upstream, mapper))
}
fun <U, T> CompletableBus<T>.map(mapper: Func1<in T, out U>): CompletableBus<U> {
    return CompletableBus.create(MapOnListen(upstream, mapper))
}

fun <T> Bus<T>.distinctUntilChanged(): Bus<T> {
    return Bus.createUnsafe(DistinctUntilChangedOnListen<T, Any>(upstream))
}
fun <T> CompletableBus<T>.distinctUntilChanged(): CompletableBus<T> {
    return CompletableBus.create(DistinctUntilChangedOnListen<T, Any>(upstream))
}

fun <U, T> Bus<T>.distinctUntilChanged(keySelector: Func1<in T, out U>): Bus<T> {
    return Bus.createUnsafe(DistinctUntilChangedOnListen(upstream, keySelector))
}
fun <U, T> CompletableBus<T>.distinctUntilChanged(keySelector: Func1<in T, out U>): CompletableBus<T> {
    return CompletableBus.create(DistinctUntilChangedOnListen(upstream, keySelector))
}

fun <T> Bus<T>.distinctUntilChanged(comparator: Func2<in T, in T, Boolean>): Bus<T> {
    return Bus.createUnsafe(DistinctUntilChangedOnListen(upstream, comparator))
}
fun <T> CompletableBus<T>.distinctUntilChanged(comparator: Func2<in T, in T, Boolean>): CompletableBus<T> {
    return CompletableBus.create(DistinctUntilChangedOnListen(upstream, comparator))
}

fun <T> Bus<T>.doOnEvent(action: Action1<in T>): Bus<T> {
    return Bus.createUnsafe(DoOnEventOnListen(upstream, action))
}
fun <T> CompletableBus<T>.doOnEvent(action: Action1<in T>): CompletableBus<T> {
    return CompletableBus.create(DoOnEventOnListen(upstream, action))
}

fun <T> Bus<T>.observeOn(scheduler: Scheduler): Bus<T> {
    return Bus.createUnsafe(ObserveOnListen(upstream, scheduler))
}
fun <T> CompletableBus<T>.observeOn(scheduler: Scheduler): CompletableBus<T> {
    return CompletableBus.create(ObserveOnListen(upstream, scheduler))
}


typealias OnListen<T> = (consumer: CompletableBusConsumer<T>) -> Cancellable

abstract class Bus<out T> constructor(internal val upstream: OnListen<T>) {

    abstract fun listen(consumer: BusConsumer<T>): Cancellable

    abstract fun listen(consumer: Action1<T>): Cancellable


    companion object {

        fun <T> create(onListen: (consumer: BusConsumer<T>) -> Cancellable): Bus<T> {
            return BusDispatcherImpl(onListen)
        }

        fun <T> concat(value: T, bus: Bus<T>): Bus<T> {
            return create { consumer ->
                consumer.onEvent(value)
                bus.listen(consumer)
            }
        }

        /**
         * This is intended for operators reuse only.
         * If client creates bus this way it could complete consumer,
         * which results in `EndlessBusCompletedException`
         */
        fun <T> createUnsafe(onListen: OnListen<T>): Bus<T> {
            return BusDispatcherImpl(onListen)
        }
    }
}

abstract class CompletableBus<out T> constructor(internal val upstream: OnListen<T>) {

    abstract fun listen(consumer: CompletableBusConsumer<T>): Cancellable

    abstract fun listen(onEvent: Action1<T>, onError: Action1<Throwable>): Cancellable

    abstract fun listen(onEvent: Action1<T>, onError: Action1<Throwable>, onComplete: Action0): Cancellable


    companion object {

        fun <T> create(onListen: OnListen<T>): CompletableBus<T> {
            return CompletableBusDispatcherImpl(onListen)
        }

        fun <T> completed(): CompletableBus<T> {
            val bus = CompletableBusDispatcher.create<T>()
            bus.onComplete()
            return bus
        }

        fun <T> concat(value: T, bus: CompletableBus<T>): CompletableBus<T> {
            return create { consumer ->
                consumer.onEvent(value)
                bus.listen(consumer)
            }
        }
    }
}

abstract class BusDispatcher<T> constructor(upstream: OnListen<T>) : BusConsumer<T>, Bus<T>(upstream) {

    abstract fun hasValue(): Boolean

    abstract val value: T

    companion object {

        fun <T> create(): BusDispatcher<T> {
            return BusDispatcherImpl()
        }

        fun <T> create(initialValue: T): BusDispatcher<T> {
            return BusDispatcherImpl(initialValue)
        }
    }
}

abstract class CompletableBusDispatcher<T> constructor(upstream: OnListen<T>) : CompletableBusConsumer<T>, CompletableBus<T>(upstream) {

    abstract fun hasValue(): Boolean

    @Throws(BusCompletedException::class, BusNoValueException::class)
    abstract fun value(): T

    companion object {

        fun <T> create(): CompletableBusDispatcher<T> {
            return CompletableBusDispatcherImpl()
        }

        fun <T> create(initialValue: T): CompletableBusDispatcher<T> {
            return CompletableBusDispatcherImpl(initialValue)
        }
    }
}