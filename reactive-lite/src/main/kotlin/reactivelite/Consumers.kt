package reactivelite

import java.util.concurrent.atomic.AtomicBoolean

interface TaskConsumer<in T> {
    fun onSuccess(event: T)
    fun onError(t: Throwable)
}

interface TaskAsyncConsumer<in T> : TaskConsumer<T> {
    fun setOnCancelled(cancellable: Cancellable)
}

interface SignalConsumer {
    fun onSignal()
    fun onError(t: Throwable)
}

interface SignalAsyncConsumer : SignalConsumer {
    fun setOnCancelled(cancellable: Cancellable)
}

interface BusConsumer<in T> {
    fun onEvent(event: T)
}

interface CompletableBusConsumer<in T> : BusConsumer<T> {
    override fun onEvent(event: T)
    fun onError(throwable: Throwable)
    fun onComplete()
}

class SafeBusConsumer<in T>(private val consumer: CompletableBusConsumer<T>) : AtomicBoolean(), CompletableBusConsumer<T> {

    override fun onEvent(event: T) {
        if (!get()) {
            consumer.onEvent(event)
        }
    }

    override fun onError(throwable: Throwable) {
        if (compareAndSet(false, true)) {
            consumer.onError(throwable)
        }
    }

    override fun onComplete() {
        if (compareAndSet(false, true)) {
            consumer.onComplete()
        }
    }

    companion object {

        private val serialVersionUID = 4184157508776082896L
    }
}
