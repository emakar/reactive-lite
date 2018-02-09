@file:Suppress("unused") // public api

package reactivelite

import reactivelite.operators.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.atomic.AtomicReference


typealias OnTaskStart<T> = (consumer: TaskConsumer<T>) -> Cancellable
typealias OnTaskStartAsync<T> = (consumer: TaskAsyncConsumer<T>) -> Unit


fun <T, S> Task<T>.map(mapper: Func1<in T, out S>): Task<S> {
    return Task(MapOperator(onStart, mapper))
}

fun <T> Task<T>.doOnStart(onStart: Action0): Task<T> {
    return Task(DoOnStartOperator(this.onStart, onStart))
}

fun <T> Task<T>.doOnSuccess(onSuccess: Action1<in T>): Task<T> {
    return Task(DoOnTerminateOperator(onStart, onSuccess, emptyAction()))
}

fun <T> Task<T>.doOnError(onError: Action1<Throwable>): Task<T> {
    return Task(DoOnTerminateOperator(onStart, emptyAction(), onError))
}

fun <T> Task<T>.doOnTerminate(onTerminate: Action0): Task<T> {
    return Task(DoOnTerminateOperator(onStart, { _ -> onTerminate.invoke() }, { _ -> onTerminate.invoke() }))
}

fun <T> Task<T>.doOnCancel(onCancel: Action0): Task<T> {
    return Task(DoOnCancelOperator(onStart, onCancel))
}

fun <I, O> Task<I>.flatMap(taskMapper: Func1<in I, out Task<out O>>): Task<O> {
    return Task(FlatMapOperator(this, taskMapper))
}

fun <T> Task<T>.observeOn(scheduler: Scheduler): Task<T> {
    return Task(ObserveOnOperator(onStart, scheduler))
}


@Throws(ExecutionException::class, InterruptedException::class)
fun <T> Task<T>.await(): T {
    val throwable = AtomicReference<Throwable>()
    val value = AtomicReference<T>()
    val latch = CountDownLatch(1)
    val cancellable = onStart.invoke(object : TaskConsumer<T> {
        override fun onSuccess(event: T) {
            value.set(event)
            latch.countDown()
        }

        override fun onError(t: Throwable) {
            throwable.set(t)
            latch.countDown()
        }
    })
    try {
        latch.await()
    } catch (t: InterruptedException) {
        cancellable.cancel()
        throw t
    }

    val local = throwable.get()
    if (local != null) {
        throw ExecutionException(local)
    }
    return value.get()
}


open class Task<T> internal constructor(internal val onStart: OnTaskStart<in T>) {

    fun start(): Cancellable {
        return start(emptyAction())
    }

    open fun start(onSuccess: Action1<in T>): Cancellable {
        return start(object : TaskConsumer<T> {
            override fun onSuccess(event: T) {
                onSuccess.invoke(event)
            }

            override fun onError(t: Throwable) {
                throw OnErrorNotImplementedException(t)
            }
        })
    }

    open fun start(onSuccess: Action1<in T>, onError: Action1<Throwable>): Cancellable {
        return start(object : TaskConsumer<T> {
            override fun onSuccess(event: T) {
                onSuccess.invoke(event)
            }

            override fun onError(t: Throwable) {
                onError.invoke(t)
            }
        })
    }

    fun start(consumer: TaskConsumer<T>): Cancellable {
        return try {
            onStart.invoke(SafeConsumer(consumer))
        } catch (e: Throwable) {
            throwIfFatal(e)
            consumer.onError(e)
            nonCancellable()
        }
    }


    companion object {

        fun <T> create(onStart: OnTaskStart<T>): Task<T> {
            return Task(onStart)
        }

        fun <T> create(scheduler: Scheduler, onStart: OnTaskStartAsync<T>): Task<T> {
            return create(ScheduledStart(onStart, scheduler))
        }

        fun <T> fromCallable(scheduler: Scheduler, callable: Callable<out T>): Task<T> {
            return create(scheduler, FromCallableStart(callable))
        }

        fun <T> just(t: T): Task<T> {
            return create { downstream ->
                downstream.onSuccess(t)
                nonCancellable()
            }
        }

        fun <T> error(t: Throwable): Task<T> {
            return create { downstream ->
                downstream.onError(t)
                nonCancellable()
            }
        }

        fun <T1, T2, S> zip(task1: Task<out T1>,
                            task2: Task<out T2>,
                            zipFunction: Func2<in T1, in T2, out S>): Task<S> {
            return create(ZipOperator(task1, task2, zipFunction))
        }
    }
}

internal class SafeConsumer<T>(consumer: TaskConsumer<T>) : AtomicReference<TaskConsumer<T>>(), TaskConsumer<T> {
    companion object {
        private val serialVersionUID = 6601656514043051006L
    }

    init {
        set(consumer)
    }

    override fun onSuccess(event: T) {
        val consumer = getAndSet(null)
        consumer?.onSuccess(event)
    }

    override fun onError(t: Throwable) {
        val consumer = getAndSet(null)
        if (consumer != null) {
            consumer.onError(t)
        } else {
            FallbackErrorHandler.handle(ErrorNotDeliveredException(t))
        }
    }
}
