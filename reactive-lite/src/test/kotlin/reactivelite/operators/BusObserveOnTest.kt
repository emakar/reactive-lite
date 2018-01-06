package reactivelite.operators

import org.junit.After
import org.junit.Assert
import org.junit.Test
import reactivelite.*
import reactivelite.utils.SpyErrorHandler
import reactivelite.utils.ioScheduler
import reactivelite.utils.singleThreaded
import java.util.concurrent.TimeUnit

class BusObserveOnTest {

    @After
    fun resetErrorHandler() {
        SpyErrorHandler.reset()
    }

    @Test
    fun observeOn() {
        val publisher = BusDispatcher.create<Int>()

        val bus = publisher
                .observeOn(singleThreaded())

        publisher.onEvent(1)

        val lastSeenThread = TestBusConsumer.test(bus)
                .awaitValueCount(1, 50, TimeUnit.MILLISECONDS)
                .lastSeenThread

        Assert.assertNotEquals(Thread.currentThread(), lastSeenThread)
    }

    @Test
    fun latestValueObserveOn() {
        val bus = BusDispatcher.create(1)
                .observeOn(singleThreaded())

        val lastSeenThread = TestBusConsumer.test(bus)
                .awaitValueCount(1, 50, TimeUnit.MILLISECONDS)
                .lastSeenThread

        Assert.assertNotEquals(Thread.currentThread(), lastSeenThread)
    }

    @Test
    fun errorBeforeObserveOnEndlessBus() {
        val spyErrorHandler = SpyErrorHandler.makeDefault()

        val publisher = BusDispatcher.create<Int>()
        val consumer = TestBusConsumer.test(publisher
                .filter { integer -> integer == 0 }
                .distinctUntilChanged { _ -> throw IllegalStateException() }
                .observeOn(ioScheduler()))

        publisher.onEvent(0)
        consumer.awaitTerminalEvent(200, TimeUnit.MILLISECONDS)
        spyErrorHandler.assertExceptions(IllegalStateException::class.java, EndlessBusCompletedException::class.java)
    }

    @Test
    fun errorBeforeObserveOnCompletableBus() {
        val publisher = CompletableBusDispatcher.create<Int>()

        val bus = publisher
                .filter { integer -> integer == 0 }
                .distinctUntilChanged { _ -> throw IllegalStateException() }
                .observeOn(ioScheduler())

        val consumer = TestBusConsumer.test(bus)

        publisher.onEvent(0)
        consumer
                .awaitTerminalEvent(100, TimeUnit.MILLISECONDS)
                .assertError(IllegalStateException::class.java)
    }

    @Test
    fun errorAfterObserveOnEndlessBus() {
        val spyErrorHandler = SpyErrorHandler.makeDefault()

        val publisher = BusDispatcher.create<Int>()
        val consumer = TestBusConsumer.test(publisher
                .filter { integer -> integer == 0 }
                .observeOn(ioScheduler())
                .distinctUntilChanged { _ -> throw IllegalStateException() })

        publisher.onEvent(0)
        consumer.awaitTerminalEvent(200, TimeUnit.MILLISECONDS)
        spyErrorHandler.assertExceptions(IllegalStateException::class.java, EndlessBusCompletedException::class.java)
    }
}
