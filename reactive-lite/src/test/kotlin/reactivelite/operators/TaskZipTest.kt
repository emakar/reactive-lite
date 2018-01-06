package reactivelite.operators

import org.junit.Assert
import org.junit.Test
import reactivelite.Task
import reactivelite.TestTaskConsumer
import reactivelite.doOnSuccess
import reactivelite.utils.TEST_VALUE
import reactivelite.utils.newAsyncTask
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger

class TaskZipTest {

    @Test
    fun zip() {
        val task = Task.zip(Task.just(true), Task.just(true)) { _, _ -> TEST_VALUE }

        TestTaskConsumer.test(task)
                .assertSuccess(TEST_VALUE)
    }

    @Test
    fun zipError() {
        val task = Task.zip<Any, Boolean, Int>(
                Task.error(IllegalStateException()),
                Task.just(true)
        ) { _, _ -> TEST_VALUE }

        TestTaskConsumer.test(task)
                .assertError(IllegalStateException::class.java)
    }

    @Test
    fun zipOnce() {
        val coordinatingLatch = CountDownLatch(2)
        val safeAwait = {
            try {
                coordinatingLatch.await()
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }

        val hits = AtomicInteger(0)
        val task = Task.zip<Int, Int, Int>(
                newAsyncTask(1)
                        .doOnSuccess {
                            coordinatingLatch.countDown()
                            safeAwait.invoke()
                        },
                newAsyncTask(1)
                        .doOnSuccess {
                            coordinatingLatch.countDown()
                            safeAwait.invoke()
                        }
        ) { _, _ -> TEST_VALUE }
                .doOnSuccess { hits.incrementAndGet() }

        TestTaskConsumer.test(task)
                .awaitTerminalEvent()
                .assertSuccess(TEST_VALUE)

        Assert.assertEquals(1, hits.get().toLong())
    }
}
