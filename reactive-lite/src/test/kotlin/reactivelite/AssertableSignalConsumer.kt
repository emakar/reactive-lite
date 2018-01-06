package reactivelite

import org.junit.Assert
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

interface AssertableSignalConsumer : SignalConsumer {

    val lastSeenThread: Thread?

    fun awaitTerminalEvent(): AssertableSignalConsumer

    fun awaitTerminalEvent(timeout: Long, unit: TimeUnit): AssertableSignalConsumer

    // TODO: this is wrong, but since consumer doesn't support cancellation we are unaware of possible values
    fun assertNoValues(): AssertableSignalConsumer

    fun assertSignal(): AssertableSignalConsumer

    fun assertError(clazz: Class<out Throwable>): AssertableSignalConsumer

    fun assertError(throwable: Throwable): AssertableSignalConsumer

    //    todo
    //    AssertableBusConsumer<T> assertCancelled();
}

internal class TestSignalConsumer : AssertableSignalConsumer {

    private val counter = AtomicInteger()
    private val throwables = CopyOnWriteArrayList<Throwable>()
    private val latch = CountDownLatch(1)
    @Volatile private var _lastSeenThread: Thread? = null

    override val lastSeenThread: Thread?
        get() = _lastSeenThread

    override fun onSignal() {
        _lastSeenThread = Thread.currentThread()
        counter.incrementAndGet()
        latch.countDown()
    }

    override fun onError(t: Throwable) {
        _lastSeenThread = Thread.currentThread()
        throwables.add(t)
        latch.countDown()
    }

    override fun awaitTerminalEvent(): AssertableSignalConsumer {
        return awaitTerminalEvent(10, TimeUnit.SECONDS)
    }

    override fun awaitTerminalEvent(timeout: Long, unit: TimeUnit): AssertableSignalConsumer {
        try {
            // todo: assert return value once consumer is aware of cancellation
            latch.await(timeout, unit)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        }

        return this
    }

    override fun assertNoValues(): AssertableSignalConsumer {
        Assert.assertEquals(0, counter.get().toLong())
        Assert.assertEquals(emptyList<Any>(), throwables)
        return this
    }

    override fun assertSignal(): AssertableSignalConsumer {
        Assert.assertEquals(1, counter.get().toLong())
        Assert.assertTrue(throwables.isEmpty())
        return this
    }

    override fun assertError(clazz: Class<out Throwable>): AssertableSignalConsumer {
        val contents = throwables.toTypedArray()
        Assert.assertEquals(1, contents.size.toLong())
        Assert.assertEquals(clazz, contents[0].javaClass)
        Assert.assertEquals(0, counter.get().toLong())
        return this
    }

    override fun assertError(throwable: Throwable): AssertableSignalConsumer {
        assertContents(throwable, throwables.toTypedArray())
        Assert.assertEquals(0, counter.get().toLong())
        return this
    }

    companion object {

        fun test(signal: Signal): AssertableSignalConsumer {
            val consumer = TestSignalConsumer()
            signal.start(consumer)
            return consumer
        }

        fun cancelAndTest(signal: Signal): AssertableSignalConsumer {
            val consumer = TestSignalConsumer()
            signal.start(consumer).cancel()
            return consumer
        }

        private fun <T> assertContents(t: T, contents: Array<*>) {
            Assert.assertEquals(1, contents.size.toLong())
            Assert.assertEquals(t, contents[0])
        }
    }
}
