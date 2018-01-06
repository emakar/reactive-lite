package reactivelite.operators

import reactivelite.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

internal class ZipOperator<T1, T2, out S>(private val task1: Task<out T1>,
                                          private val task2: Task<out T2>,
                                          private val zipFunction: Func2<in T1, in T2, out S>) : OnTaskStart<S> {

    override fun invoke(downstream: TaskConsumer<S>): Cancellable {
        return ZipEmitter(downstream).start()
    }

    private inner class ZipEmitter internal constructor(private val downstream: TaskConsumer<S>) : Cancellable {

        @Volatile private var t1: T1? = null
        @Volatile private var t2: T2? = null

        private val remaining = AtomicInteger(2)
        private val hasError = AtomicBoolean(false)

        @Volatile private var t1Cancellable = nonCancellable()
        @Volatile private var t2Cancellable = nonCancellable()

        fun start(): Cancellable {
            return try {
                t1Cancellable = task1.start({ this.task1Finished(it) }, { this.onError(it) })
                t2Cancellable = task2.start({ this.task2Finished(it) }, { this.onError(it) })
                this
            } catch (t: Throwable) {
                throwIfFatal(t)
                onError(t)
                nonCancellable()
            }
        }

        private fun task1Finished(t: T1) {
            t1 = t
            zipIfAllFinished()
        }

        private fun task2Finished(t: T2) {
            t2 = t
            zipIfAllFinished()
        }

        private fun zipIfAllFinished() {
            if (remaining.decrementAndGet() > 0) {
                return
            }

            val t1 = this.t1
            if (t1 == null) {
                onError(NullPointerException("finished with null event"))
                return
            }

            val t2 = this.t2
            if (t2 == null) {
                onError(NullPointerException("finished with null event"))
                return
            }

            try {
                val s = zipFunction.invoke(t1, t2)
                downstream.onSuccess(s)
            } catch (t: Throwable) {
                throwIfFatal(t)
                onError(t)
            }

        }

        private fun onError(throwable: Throwable) {
            if (hasError.compareAndSet(false, true)) {
                downstream.onError(throwable)
            }
        }

        override fun invoke() {
            try {
                t1Cancellable.cancel()
                t2Cancellable.cancel()
            } catch (t: Throwable) {
                throwIfFatal(t)
                onError(t)
            }
        }
    }
}
