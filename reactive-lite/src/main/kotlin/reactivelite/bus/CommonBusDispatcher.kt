package reactivelite.bus

import reactivelite.*
import java.util.concurrent.CopyOnWriteArrayList

internal class CommonBusDispatcher<T> : OnListen<T>, BusConsumer<T>, CompletableBusConsumer<T> {

    private var latestValue: T?
    private val consumers = CopyOnWriteArrayList<CompletableBusConsumer<T>>()
    internal val upstream: OnListen<T>
    private val lock: Any = Any()
    private var throwable: Throwable? = null
    private var completed: Boolean = false

    private val isDone: Boolean
        get() = completed || throwable != null

    internal constructor(initialValue: T? = null) {
        this.latestValue = initialValue
        this.upstream = this
    }

    internal constructor(onListen: OnListen<T>) {
        this.latestValue = null
        this.upstream = onListen
    }

    fun listen(consumer: BusConsumer<T>): Cancellable {
        return listen(object : CompletableBusConsumer<T> {
            override fun onEvent(event: T) {
                consumer.onEvent(event)
            }

            override fun onError(throwable: Throwable) {
                throw EndlessBusCompletedException("throwable in endless bus", throwable)
            }

            override fun onComplete() {
                throw EndlessBusCompletedException("completed in endless bus")
            }
        })
    }

    fun listen(consumer: Action1<T>): Cancellable {
        return listen(object : BusConsumer<T> {
            override fun onEvent(event: T) {
                consumer.invoke(event)
            }
        })
    }

    fun listen(onEvent: Action1<T>, onError: Action1<Throwable>): Cancellable {
        return listen(onEvent, onError, emptyAction0())
    }

    fun listen(onEvent: Action1<T>, onError: Action1<Throwable>, onComplete: Action0): Cancellable {
        return listen(object : CompletableBusConsumer<T> {
            override fun onEvent(event: T) {
                onEvent.invoke(event)
            }

            override fun onError(throwable: Throwable) {
                onError.invoke(throwable)
            }

            override fun onComplete() {
                onComplete.invoke()
            }
        })
    }

    fun listen(consumer: CompletableBusConsumer<T>): Cancellable {
        return upstream.invoke(consumer)
    }

    override fun invoke(consumer: CompletableBusConsumer<T>): Cancellable {
        val downstream = SafeBusConsumer(consumer)
        try {
            var localLatestValue: T? = null
            var localThrowable: Throwable? = null
            var localDone = false

            synchronized(lock) {
                localLatestValue = latestValue
                localThrowable = throwable
                localDone = isDone
                if (!localDone) {
                    consumers.add(downstream)
                }
            }

            if (localLatestValue != null && !localDone) {
                downstream.onEvent(localLatestValue!!)
            } else if (localDone) {
                if (localThrowable != null) {
                    downstream.onError(localThrowable!!)
                } else {
                    downstream.onComplete()
                }
                return nonCancellable()
            }
            return { disconnect(downstream) }

        } catch (t: Throwable) {
            throwIfFatal(t)
            downstream.onError(t)
            return nonCancellable()
        }
    }

    private fun disconnect(consumer: CompletableBusConsumer<T>) {
        synchronized(lock) {
            consumers.remove(consumer)
        }
    }

    override fun onEvent(event: T) {
        try {
            var iterator: Iterator<CompletableBusConsumer<T>>? = null
            synchronized(lock) {
                if (isDone) {
                    return
                }
                latestValue = event
                iterator = consumers.iterator()
            }

            val it = iterator
            if (it == null) {
                return
            }
            while (it.hasNext()) {
                it.next().onEvent(event)
            }
        } catch (throwable: Throwable) {
            throwIfFatal(throwable)
            onError(throwable)
        }

    }

    override fun onError(throwable: Throwable) {
        throwIfFatal(throwable)
        try {
            var iterator: Iterator<CompletableBusConsumer<T>>? = null
            synchronized(lock) {
                if (isDone) {
                    return
                }
                this.throwable = throwable
                iterator = consumers.iterator()
                consumers.clear()
            }

            val it = iterator
            if (it == null) {
                return
            }
            while (it.hasNext()) {
                it.next().onError(throwable)
            }
        } catch (t: Throwable) {
            throwIfFatal(t)
            throw IllegalStateException("error while handling onError", t)
        }

    }

    override fun onComplete() {
        try {
            var iterator: Iterator<CompletableBusConsumer<T>>? = null
            synchronized(lock) {
                if (isDone) {
                    return
                }
                completed = true
                iterator = consumers.iterator()
                consumers.clear()
            }

            val it = iterator
            if (it == null) {
                return
            }
            while (it.hasNext()) {
                it.next().onComplete()
            }
        } catch (t: Throwable) {
            throwIfFatal(t)
            throw IllegalStateException("error while handling onComplete", t)
        }

    }

    fun hasValue(): Boolean {
        synchronized(lock) {
            return latestValue != null || throwable != null || completed
        }
    }

    @Throws(BusCompletedException::class, BusNoValueException::class)
    fun value(): T {
        synchronized(lock) {
            val local = latestValue
            return when {
                throwable != null -> throw BusCompletedException("bus completed with error", throwable)
                completed -> throw BusCompletedException("bus completed normally")
                local == null -> throw BusNoValueException("no value in bus")
                else -> local
            }
        }
    }
}
