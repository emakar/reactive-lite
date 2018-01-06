package reactivelite.utils

import reactivelite.*
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicInteger

val TEST_VALUE = 100500

fun singleThreaded(): Scheduler {
    val executor = Executors.newSingleThreadExecutor(NamedThreadFactory.create("test-single"))
    return fromExecutor(executor)
}

fun newAsyncTask(value: Int): Task<Int> {
    return Task.create { taskConsumer ->
        val cancellable = StatefulCancellable()
        Thread(CancellationAwareProducer(cancellable, taskConsumer, value)).start()
        cancellable
    }
}

fun ioScheduler(): Scheduler = ioScheduler

private val ioScheduler: Scheduler by lazy {
    val executor = Executors.newCachedThreadPool(NamedThreadFactory.create("rotor-io"))
    fromExecutor(executor, true)
}

internal class CancellationAwareProducer<T>(private val cancellable: StatefulCancellable,
                                            private val consumer: TaskConsumer<T>,
                                            private val value: T) : Runnable {
    override fun run() {
        try {
            Thread.sleep(100)
        } catch (ignored: InterruptedException) {
        }

        if (!cancellable.isCancelled) {
            consumer.onSuccess(value)
        }
    }
}

class NamedThreadFactory private constructor(private val namePrefix: String) : ThreadFactory {

    private val threadNumber = AtomicInteger()

    override fun newThread(runnable: Runnable): Thread {
        return Thread(runnable, namePrefix + threadNumber.incrementAndGet())
    }

    companion object {

        fun createWithPoolName(poolName: String, threadNamePrefix: String): ThreadFactory {
            return NamedThreadFactory(threadNamePrefix
                    + "-"
                    + poolName
                    + "-thread-")
        }

        fun create(threadNamePrefix: String): ThreadFactory {
            return NamedThreadFactory(threadNamePrefix + "-thread-")
        }
    }
}