package reactivelite

import org.junit.Assert
import java.util.*
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

interface AssertableBusConsumer<T> : CompletableBusConsumer<T> {

    val lastSeenThread: Thread?

    fun awaitTerminalEvent(): AssertableBusConsumer<T>

    fun awaitTerminalEvent(timeout: Long, unit: TimeUnit): AssertableBusConsumer<T>

    fun awaitValueCount(expected: Int, timeout: Long, unit: TimeUnit): AssertableBusConsumer<T>

    fun assertTerminalEvent(): AssertableBusConsumer<T>

    fun assertNoTerminalEvent(): AssertableBusConsumer<T>

    fun assertCompleted(): AssertableBusConsumer<T>

    fun assertNotCompleted(): AssertableBusConsumer<T>

    fun assertError(clazz: Class<out Throwable>): AssertableBusConsumer<T>

    fun assertError(throwable: Throwable): AssertableBusConsumer<T>

    fun assertNoErrors(): AssertableBusConsumer<T>

    fun assertValues(vararg values: T): AssertableBusConsumer<T>

    fun assertValues(items: List<T>): AssertableBusConsumer<T>

    fun assertValue(value: T): AssertableBusConsumer<T>

    fun assertValueCount(count: Int): AssertableBusConsumer<T>

    fun assertNoValues(): AssertableBusConsumer<T>

    //    todo
    //    AssertableBusConsumer<T> assertCancelled();
}

open class TestBusConsumer<T> : AssertableBusConsumer<T> {

    // todo: replace client synchronization
    private val values = CopyOnWriteArrayList<T>()
    private val throwables = CopyOnWriteArrayList<Throwable>()
    private val completions = AtomicInteger()
    private val latch = CountDownLatch(1)
    private val valuesAwaitLock = java.lang.Object()
    @Volatile private var _lastSeenThread: Thread? = null

    override val lastSeenThread: Thread?
        get() = _lastSeenThread

    override fun onEvent(event: T) {
        _lastSeenThread = Thread.currentThread()
        values.add(event)
        synchronized(valuesAwaitLock) {
            valuesAwaitLock.notifyAll()
        }
    }

    override fun onError(throwable: Throwable) {
        _lastSeenThread = Thread.currentThread()
        throwables.add(throwable)
        latch.countDown()
        synchronized(valuesAwaitLock) {
            valuesAwaitLock.notifyAll()
        }
    }

    override fun onComplete() {
        _lastSeenThread = Thread.currentThread()
        completions.incrementAndGet()
        latch.countDown()
        synchronized(valuesAwaitLock) {
            valuesAwaitLock.notifyAll()
        }
    }

    override fun awaitTerminalEvent(): AssertableBusConsumer<T> {
        // to not hang vm on failing test
        return awaitTerminalEvent(10, TimeUnit.SECONDS)
    }

    override fun awaitTerminalEvent(timeout: Long, unit: TimeUnit): AssertableBusConsumer<T> {
        try {
            // todo: assert return value once consumer is aware of cancellation
            latch.await(timeout, unit)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        }

        return this
    }

    override fun awaitValueCount(expected: Int, timeout: Long, unit: TimeUnit): AssertableBusConsumer<T> {
        var timeoutVar = timeout
        var valueCount = valueCount()

        while (timeoutVar != 0L && valueCount < expected) {
            if (completions.get() > 0 || throwables.size > 0) {
                throw IllegalStateException("completed without receiving $expected values, received $valueCount")
            }

            try {
                synchronized(valuesAwaitLock) {
                    valuesAwaitLock.wait(unit.toMillis(1))
                }
            } catch (e: InterruptedException) {
                throw IllegalStateException("Interrupted", e)
            }

            timeoutVar--
            valueCount = valueCount()
        }

        if (valueCount < expected) {
            throw IllegalStateException("not yet received $expected values, received $valueCount")
        }

        return this
    }

    override fun assertTerminalEvent(): AssertableBusConsumer<T> {
        val completions = this.completions.get()
        val errors = throwables.size
        if (completions > 0) {
            Assert.assertEquals(1, completions.toLong())
            Assert.assertEquals(0, errors.toLong())
        }
        if (errors > 0) {
            Assert.assertEquals(0, completions.toLong())
            Assert.assertEquals(1, errors.toLong())
        }
        return this
    }

    override fun assertNoTerminalEvent(): AssertableBusConsumer<T> {
        assertNotCompleted()
        assertNoErrors()
        return this
    }

    override fun assertCompleted(): AssertableBusConsumer<T> {
        Assert.assertEquals(1, completions.get().toLong())
        assertNoErrors()
        return this
    }

    override fun assertNotCompleted(): AssertableBusConsumer<T> {
        Assert.assertEquals(0, completions.get().toLong())
        return this
    }

    override fun assertError(clazz: Class<out Throwable>): AssertableBusConsumer<T> {
        Assert.assertEquals(1, throwables.size.toLong())
        Assert.assertEquals(clazz, throwables[0].javaClass)
        assertNotCompleted()
        return this
    }

    override fun assertError(throwable: Throwable): AssertableBusConsumer<T> {
        Assert.assertEquals(1, throwables.size.toLong())
        Assert.assertEquals(throwable, throwables[0])
        return this
    }

    override fun assertNoErrors(): AssertableBusConsumer<T> {
        Assert.assertEquals(0, throwables.size.toLong())
        return this
    }

    @SafeVarargs
    override fun assertValues(vararg values: T): AssertableBusConsumer<T> {
        val list = ArrayList<T>(values.size)
        Collections.addAll(list, *values)
        assertValues(list)
        return this
    }

    override fun assertValues(items: List<T>): AssertableBusConsumer<T> {
        Assert.assertEquals(items, values)
        return this
    }

    override fun assertValue(value: T): AssertableBusConsumer<T> {
        val values = this.values.toArray()
        Assert.assertEquals(value, values[values.size - 1])
        return this
    }

    override fun assertValueCount(count: Int): AssertableBusConsumer<T> {
        Assert.assertEquals(count.toLong(), valueCount().toLong())
        return this
    }

    override fun assertNoValues(): AssertableBusConsumer<T> {
        Assert.assertEquals(0, valueCount().toLong())
        return this
    }

    private fun valueCount(): Int {
        return values.size
    }

    companion object {

        fun <T> test(bus: Bus<T>): AssertableBusConsumer<T> {
            val consumer = TestBusConsumer<T>()
            bus.listen(consumer)
            return consumer
        }

        fun <T> test(bus: CompletableBus<T>): AssertableBusConsumer<T> {
            val consumer = TestBusConsumer<T>()
            bus.listen(consumer)
            return consumer
        }

        fun <T> cancelAndTest(bus: Bus<T>): AssertableBusConsumer<T> {
            val consumer = TestBusConsumer<T>()
            bus.listen(consumer).cancel()
            return consumer
        }

        fun <T> cancelAndTest(bus: CompletableBus<T>): AssertableBusConsumer<T> {
            val consumer = TestBusConsumer<T>()
            bus.listen(consumer).cancel()
            return consumer
        }
    }
}
