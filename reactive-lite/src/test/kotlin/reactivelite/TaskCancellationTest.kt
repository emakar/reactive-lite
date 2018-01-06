package reactivelite

import org.junit.Assert
import org.junit.Test
import reactivelite.utils.ioScheduler
import reactivelite.utils.newAsyncTask
import reactivelite.utils.singleThreaded
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class TaskCancellationTest {

    @Test
    fun scheduledTaskCancelled() {
        val task = Task.create<Int>(ioScheduler()) { consumer ->
            try {
                Thread.sleep(100)
                consumer.onSuccess(123)
            } catch (ignored: InterruptedException) {
                // expected
            }
        }

        // TODO: use awaitTerminalEvent() and make it aware of both termination and cancellation
        TestTaskConsumer.cancelAndTest(task)
                .awaitTerminalEvent(300, TimeUnit.MILLISECONDS)
                .assertNoValues()
    }

    @Test
    fun scheduledRunningTaskCancelled() {
        val executionStarted = CountDownLatch(1)
        val task = Task.create<Int>(ioScheduler()) { consumer ->
            executionStarted.countDown()
            try {
                Thread.sleep(100)
            } catch (ignored: InterruptedException) {
            }

            val cancellable = StatefulCancellable()
            consumer.setOnCancelled(cancellable)
            if (!cancellable.isCancelled) {
                consumer.onSuccess(123)
            }
        }

        // TODO: use awaitTerminalEvent() and make it aware of both termination and cancellation
        val consumer = TestTaskConsumer<Int>()
        val cancellable = task.start(consumer)

        executionStarted.await()
        cancellable.cancel()

        consumer.awaitTerminalEvent(300, TimeUnit.MILLISECONDS)
                .assertNoValues()
    }

    @Test
    fun cancellationChained() {
        val nonInterruptingScheduler = singleThreaded()

        val task = Task.create<Int>(nonInterruptingScheduler) { consumer ->
            consumer.setOnCancelled { Thread.currentThread().interrupt() }
            try {
                Thread.sleep(100)
                consumer.onSuccess(123)
            } catch (ignored: InterruptedException) {
                // expected
            }
        }

        // TODO: use awaitTerminalEvent() and make it aware of both termination and cancellation
        TestTaskConsumer.cancelAndTest(task)
                .awaitTerminalEvent(300, TimeUnit.MILLISECONDS)
                .assertNoValues()
    }

    @Test
    fun asyncAsyncFlatMapCancelBoth() {
        val ref = AtomicReference<Any>()

        val asyncTask1 = newAsyncTask(1)
        val asyncTask2 = newAsyncTask(2)

        val task = asyncTask1
                .doOnSuccess { ref.set(it) }
                .flatMap { asyncTask2 }

        TestTaskConsumer.cancelAndTest(task)
                // TODO: until consumer supports cancellation (or a cancellable itself) we could only be optimistic with timeouts
                .awaitTerminalEvent(300, TimeUnit.MILLISECONDS)
                .assertNoValues()

        val value = ref.get()
        if (value != null) {
            Assert.fail("received " + value)
        }
    }

    @Test
    fun syncAsyncFlatMapCancel() {
        val task = Task.just(true)
                .flatMap { newAsyncTask(1) }

        TestTaskConsumer.cancelAndTest(task)
                .awaitTerminalEvent(200, TimeUnit.MILLISECONDS)
                .assertNoValues()
    }

    @Test
    fun multipleStartedTaskCancelled() {
        val task = newAsyncTask(0)
                .flatMap { newAsyncTask(1) }

        TestTaskConsumer.cancelAndTest(task)
                .awaitTerminalEvent(300, TimeUnit.MILLISECONDS)
                .assertNoValues()

        TestTaskConsumer.cancelAndTest(task)
                .awaitTerminalEvent(300, TimeUnit.MILLISECONDS)
                .assertNoValues()
    }

    @Test
    fun taskCancellationIndependent() {
        val task = newAsyncTask(0)
                .flatMap { newAsyncTask(1) }

        val firstLaunch = TestTaskConsumer.test(task)

        TestTaskConsumer.cancelAndTest(task)
                .awaitTerminalEvent(300, TimeUnit.MILLISECONDS)
                .assertNoValues()

        firstLaunch.awaitTerminalEvent(300, TimeUnit.MILLISECONDS)
                .assertSuccess(1)
    }

    @Test
    fun secondTaskCancelled() {
        val asyncTask1 = newAsyncTask(1)
        val asyncTask2 = newAsyncTask(2)

        val latch = CountDownLatch(1)
        val result = AtomicInteger()

        val cancellable = asyncTask1
                .doOnSuccess { value ->
                    result.set(value)
                    latch.countDown()
                }
                .flatMap { asyncTask2 }
                .doOnSuccess { result.set(it) }
                .start()

        latch.await()
        cancellable.cancel()

        // TODO: we don't know whether asyncTask2 completes or not, so can't latch it
        Thread.sleep(300)

        Assert.assertEquals(1, result.get().toLong())
    }

    @Test
    fun zipCancelled() {
        val result = AtomicInteger()

        val asyncTask1 = newAsyncTask(1)
        val asyncTask2 = newAsyncTask(2)

        Task.zip(
                asyncTask1.doOnSuccess { result.set(it) },
                asyncTask2.doOnSuccess { result.set(it) }
        ) { v1, v2 -> v1 + v2 }
                .start { result.set(it) }
                .cancel()

        Thread.sleep(300)

        Assert.assertEquals(0, result.get().toLong())
    }
}
