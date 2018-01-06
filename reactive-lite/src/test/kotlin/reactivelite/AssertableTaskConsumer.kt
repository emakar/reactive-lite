package reactivelite

import org.junit.Assert
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

interface AssertableTaskConsumer<T> : TaskConsumer<T> {

    val lastSeenThread: Thread?

    fun awaitTerminalEvent(): AssertableTaskConsumer<T>

    fun awaitTerminalEvent(timeout: Long, unit: TimeUnit): AssertableTaskConsumer<T>

    // TODO: this is wrong, but since consumer doesn't support cancellation we are unaware of possible values
    fun assertNoValues(): AssertableTaskConsumer<T>

    fun assertSuccess(t: T): AssertableTaskConsumer<T>

    fun assertError(clazz: Class<out Throwable>): AssertableTaskConsumer<T>

    fun assertError(throwable: Throwable): AssertableTaskConsumer<T>

    //    todo
    //    AssertableBusConsumer<T> assertCancelled();
}

class TestTaskConsumer<T> : AssertableTaskConsumer<T> {

    private val values = CopyOnWriteArrayList<Any>()
    private val throwables = CopyOnWriteArrayList<Throwable>()
    private val latch = CountDownLatch(1)
    @Volatile private var _lastSeenThread: Thread? = null

    override val lastSeenThread: Thread?
        get() = _lastSeenThread

    override fun onSuccess(event: T) {
        _lastSeenThread = Thread.currentThread()
        values.add(event)
        latch.countDown()
    }

    override fun onError(t: Throwable) {
        _lastSeenThread = Thread.currentThread()
        throwables.add(t)
        latch.countDown()
    }

    override fun awaitTerminalEvent(): AssertableTaskConsumer<T> {
        // to not hang vm on failing test
        return awaitTerminalEvent(10, TimeUnit.SECONDS)
    }

    override fun awaitTerminalEvent(timeout: Long, unit: TimeUnit): AssertableTaskConsumer<T> {
        try {
            // todo: assert return value once consumer is aware of cancellation
            latch.await(timeout, unit)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        }

        return this
    }

    override fun assertNoValues(): AssertableTaskConsumer<T> {
        Assert.assertEquals(emptyList<Any>(), values)
        Assert.assertEquals(emptyList<Any>(), throwables)
        return this
    }

    override fun assertSuccess(t: T): AssertableTaskConsumer<T> {
        assertContents(t, values.toTypedArray())
        Assert.assertTrue(throwables.isEmpty())
        return this
    }

    override fun assertError(clazz: Class<out Throwable>): AssertableTaskConsumer<T> {
        val contents = throwables.toTypedArray()
        Assert.assertEquals(1, contents.size.toLong())
        Assert.assertEquals(clazz, contents[0].javaClass)
        Assert.assertTrue(values.isEmpty())
        return this
    }

    override fun assertError(throwable: Throwable): AssertableTaskConsumer<T> {
        assertContents(throwable, throwables.toTypedArray())
        Assert.assertTrue(values.isEmpty())
        return this
    }

    companion object {

        fun <T> test(task: Task<T>): AssertableTaskConsumer<T> {
            val consumer = TestTaskConsumer<T>()
            task.start(consumer)
            return consumer
        }

        fun <T> cancelAndTest(task: Task<T>): AssertableTaskConsumer<T> {
            val consumer = TestTaskConsumer<T>()
            task.start(consumer).cancel()
            return consumer
        }

        private fun <T> assertContents(t: T, contents: Array<T>) {
            Assert.assertEquals(1, contents.size.toLong())
            Assert.assertEquals(t, contents[0])
        }
    }
}
