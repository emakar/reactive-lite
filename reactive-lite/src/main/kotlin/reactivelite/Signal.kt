@file:Suppress("unused") // public api

package reactivelite

import reactivelite.operators.MergeOperator
import reactivelite.operators.ObserveOnOperator

typealias OnSignalStart = (consumer: SignalConsumer) -> Cancellable
typealias OnSignalStartAsync = (consumer: SignalAsyncConsumer) -> Unit


fun <T> Signal.observeOn(scheduler: Scheduler): Signal {
    return Signal(ObserveOnOperator(onStart, scheduler))
}

fun Signal.andThen(signal: Signal): Signal {
    return Signal(flatMap { signal })
}

fun Signal.doOnSuccess(onSuccess: Action0): Signal {
    return Signal(doOnSuccess { _ -> onSuccess.invoke() })
}

class Signal : Task<Any> {

    internal constructor(task: Task<out Any>) : super(task.onStart)

    internal constructor(onTaskStart: OnTaskStart<Any>) : super(onTaskStart)

    override fun start(onSuccess: Action1<Any>): Cancellable {
        return start(object : TaskConsumer<Any> {
            override fun onSuccess(event: Any) {
                onSuccess.invoke(event)
            }

            override fun onError(t: Throwable) {
                throw OnErrorNotImplementedException(t)
            }
        })
    }

    override fun start(onSuccess: Action1<Any>, onError: Action1<Throwable>): Cancellable {
        return start(object : TaskConsumer<Any> {
            override fun onSuccess(event: Any) {
                onSuccess.invoke(EVENT)
            }

            override fun onError(t: Throwable) {
                onError.invoke(t)
            }
        })
    }

    fun start(consumer: SignalConsumer): Cancellable {
        return start(object : TaskConsumer<Any> {
            override fun onSuccess(event: Any) {
                consumer.onSignal()
            }

            override fun onError(t: Throwable) {
                consumer.onError(t)
            }
        })
    }

    private class SignalAsyncConsumerImpl internal constructor(private val consumer: TaskAsyncConsumer<Any>) : SignalAsyncConsumer {

        override fun setOnCancelled(cancellable: Cancellable) {
            consumer.setOnCancelled(cancellable)
        }

        override fun onSignal() {
            consumer.onSuccess(EVENT)
        }

        override fun onError(t: Throwable) {
            consumer.onError(t)
        }
    }

    companion object {

        internal val EVENT = Any()

        fun create(onStart: OnSignalStart): Signal {
            return Signal { consumer ->
                onStart.invoke(object : SignalConsumer {
                    override fun onSignal() {
                        consumer.onSuccess(EVENT)
                    }

                    override fun onError(t: Throwable) {
                        consumer.onError(t)
                    }
                })
            }
        }

        fun create(scheduler: Scheduler, onStart: OnSignalStartAsync): Signal {
            return Signal(Task.create(scheduler) { consumer -> onStart.invoke(SignalAsyncConsumerImpl(consumer)) })
        }

        fun signal(): Signal {
            return create { consumer ->
                consumer.onSignal()
                nonCancellable()
            }
        }

        fun error(t: Throwable): Signal {
            return create { consumer ->
                consumer.onError(t)
                nonCancellable()
            }
        }

        fun merge(signal1: Signal, signal2: Signal): Signal {
            return Signal(MergeOperator(signal1, signal2))
        }

        fun fromTask(task: Task<out Any>): Signal {
            return Signal(task)
        }
    }
}