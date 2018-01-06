package reactivelite

import org.junit.After
import org.junit.Assert
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Ignore
import org.junit.Test
import reactivelite.utils.SpyErrorHandler
import reactivelite.utils.TEST_VALUE
import reactivelite.utils.ioScheduler
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class TaskTest {

    @After
    fun resetErrorHandler() {
        SpyErrorHandler.reset()
    }

    @Test
    @Ignore
    fun successOnce() {
        val task = Task.create<Int> { consumer ->
            consumer.onSuccess(0)
            consumer.onSuccess(1)
            nonCancellable()
        }

        TestTaskConsumer.test(task)
                // TODO: some exception
                .assertError(Exception())
    }

    @Test
    @Ignore
    fun errorOnce() {
        val task = Task.create<Int> { consumer ->
            consumer.onError(IllegalArgumentException())
            consumer.onError(IllegalStateException())
            nonCancellable()
        }

        TestTaskConsumer.test(task)
                // TODO: some exception
                .assertError(Exception())
    }

    @Test
    fun newFlowOnEachStart() {
        val hits = AtomicInteger()

        val task = Task.create<Boolean> { consumer ->
            hits.incrementAndGet()
            consumer.onSuccess(true)
            nonCancellable()
        }

        task.start()
        task.start()

        Assert.assertEquals(2, hits.get().toLong())
    }

    @Test
    fun scheduledCreate() {
        val task = Task.create<Int>(ioScheduler()) { consumer -> consumer.onSuccess(TEST_VALUE) }

        val lastSeenThread = TestTaskConsumer.test(task)
                .awaitTerminalEvent()
                .assertSuccess(TEST_VALUE)
                .lastSeenThread

        Assert.assertNotEquals(Thread.currentThread(), lastSeenThread)
    }

    @Test
    fun scheduledFromCallable() {
        val task = Task.fromCallable(ioScheduler()) { TEST_VALUE }
        val lastSeenThread = TestTaskConsumer.test(task)
                .awaitTerminalEvent()
                .assertSuccess(TEST_VALUE)
                .lastSeenThread

        Assert.assertNotEquals(Thread.currentThread(), lastSeenThread)
    }

    @Test
    fun errorInTask() {
        val task = Task.error<Int>(IllegalStateException())

        TestTaskConsumer.test(task)
                .assertError(IllegalStateException::class.java)
    }

    @Test
    fun errorInTaskNotImplemented() {
        val task = Task.error<Int>(IllegalStateException())

        try {
            task.start()
            fail()
        } catch (ignored: OnErrorNotImplementedException) {
        }

    }

    @Test
    fun errorInAsyncTaskNotImplemented() {
        val spyErrorHandler = SpyErrorHandler.makeDefault()

        val task = Task.fromCallable(ioScheduler()) { TEST_VALUE }
                .doOnSuccess { throw IllegalStateException() }

        task.start()
        Thread.sleep(100)
        spyErrorHandler.assertExceptions(IllegalStateException::class.java, OnErrorNotImplementedException::class.java)
    }

    @Test
    fun errorInAsyncTaskFromCallableNotImplemented() {
        val spyErrorHandler = SpyErrorHandler.makeDefault()

        val task = Task.fromCallable(ioScheduler()) { throw IllegalStateException() }

        task.start()
        Thread.sleep(100)
        spyErrorHandler.assertExceptions(IllegalStateException::class.java, OnErrorNotImplementedException::class.java)
    }

    @Test
    fun errorInScheduledTask() {
        val task = Task.create<Int>(ioScheduler()) { throw IllegalStateException() }

        TestTaskConsumer.test(task)
                .awaitTerminalEvent()
                .assertError(IllegalStateException::class.java)
    }

    // todo: actually subscription logic is synchronous and it's ok for rx as well to notify onError (is it a bug there?)
    @Ignore
    @Test
    fun errorInOnSuccess() {
        Task.just(true)
                .start({ throw RuntimeException("fatal") }) { fail("contract allows onSuccess or onError only") }
    }

    @Test
    fun doOnCancelCalled() {
        val taskCancellationHandled = AtomicBoolean()
        val operatorCancellationHandled = AtomicBoolean()

        val task = Task.create<Any> { { taskCancellationHandled.set(true) } }
                .doOnCancel { operatorCancellationHandled.set(true) }

        TestTaskConsumer.cancelAndTest(task)
                .awaitTerminalEvent(100, TimeUnit.MILLISECONDS)
                .assertNoValues()

        assertTrue(taskCancellationHandled.get())
        assertTrue(operatorCancellationHandled.get())
    }

    @Test
    fun asyncDoOnCancelCalled() {
        val operatorCancellationHandled = AtomicBoolean()

        val task = Task.create<Any>(ioScheduler()) {
            // this body would either be called or not
            // - it depends on schedule cancellation, so don't check it
        }
                .doOnCancel { operatorCancellationHandled.set(true) }

        TestTaskConsumer.cancelAndTest(task)
                .awaitTerminalEvent(100, TimeUnit.MILLISECONDS)
                .assertNoValues()

        assertTrue(operatorCancellationHandled.get())
    }
}