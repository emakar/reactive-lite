@file:Suppress("unused") // public api

package reactivelite

import java.util.*
import java.util.concurrent.atomic.AtomicReference

typealias Cancellable = () -> Unit

fun Cancellable.cancel() = this.invoke()

class CompositeCancellable : Cancellable {

    private val cancellables = ArrayList<Cancellable>(4)
    @Volatile private var cancelled: Boolean = false

    fun add(c: Cancellable) {
        if (!cancelled) {
            synchronized(this) {
                if (!cancelled) {
                    cancellables.add(c)
                    return
                }
            }
        }
        // call after leaving the synchronized block so we're not holding a lock while executing this
        c.cancel()
    }

    fun clear() {
        if (!cancelled) {
            synchronized(this) {
                if (!cancelled) {
                    cancelAll(cancellables)
                }
            }
        }
    }

    override fun invoke() {
        if (!cancelled) {
            synchronized(this) {
                if (cancelled) {
                    return
                }
                cancelled = true
                cancelAll(cancellables)
            }
        }
    }

    private fun cancelAll(cancellables: Collection<Cancellable>) {
        var throwables: MutableList<Throwable>? = null
        for (c in cancellables) {
            try {
                c.cancel()
            } catch (e: Throwable) {
                if (throwables == null) {
                    throwables = ArrayList()
                }
                throwables.add(e)
            }

        }
        throwIfAny(throwables)
    }
}

class SerialCancellable : Cancellable {

    private val cancellable = AtomicReference<Cancellable>()

    override fun invoke() {
        var current: Cancellable? = cancellable.get()
        if (current !== CANCELLED) {
            current = cancellable.getAndSet(CANCELLED)
            if (current != null) {
                current.cancel()
            }
        }
    }

    fun set(c: Cancellable) {
        while (true) {
            val current = cancellable.get()

            if (current === CANCELLED) {
                c.cancel()
                return
            }

            if (cancellable.compareAndSet(current, c)) {
                current?.cancel()
                return
            }
        }
    }

    fun clear() {
        val cancellable = this.cancellable.getAndSet(null)
        cancellable?.cancel()
    }

    companion object {

        private val CANCELLED = { }
    }
}

class StatefulCancellable : Cancellable {

    @Volatile
    var isCancelled: Boolean = false

    override fun invoke() {
        isCancelled = true
    }
}

