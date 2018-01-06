package reactivelite.operators

import reactivelite.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

internal class MergeOperator(private val signal1: Signal,
                             private val signal2: Signal) : OnTaskStart<Any> {

    override fun invoke(downstream: TaskConsumer<Any>): Cancellable {
        return MergeConsumer(signal1, signal2, downstream)
    }

    private class MergeConsumer internal constructor(signal1: Signal,
                                                     signal2: Signal,
                                                     private val downstream: TaskConsumer<Any>) : SignalConsumer, Cancellable {
        private val cancellable = CompositeCancellable()
        private val counter = AtomicInteger(2)
        private val hasError = AtomicBoolean()

        init {
            cancellable.add(signal1.start(this))
            cancellable.add(signal2.start(this))
        }

        override fun onSignal() {
            if (counter.decrementAndGet() == 0) {
                downstream.onSuccess(Signal.EVENT)
            }
        }

        override fun onError(t: Throwable) {
            if (hasError.compareAndSet(false, true)) {
                cancel()
                downstream.onError(t)
            }
        }

        override fun invoke() {
            cancellable.cancel()
        }
    }
}
