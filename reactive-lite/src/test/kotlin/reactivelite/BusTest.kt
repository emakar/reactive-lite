package reactivelite

import org.junit.Assert.fail
import org.junit.Test
import reactivelite.utils.TEST_VALUE
import reactivelite.utils.singleThreaded
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicReference

class BusTest {

    @Test
    fun smoke() {
        val bus = CompletableBusDispatcher.create<Boolean>()

        val consumer = TestBusConsumer.test(bus)

        bus.onEvent(true)
        bus.onEvent(false)
        bus.onEvent(true)
        bus.onComplete()

        consumer.assertValues(true, false, true)
                .assertCompleted()
    }

    @Test
    fun completed() {
        val bus = CompletableBusDispatcher.create<Any>()
        bus.onComplete()
        TestBusConsumer.test(bus).assertCompleted()
    }

    @Test
    fun initialValue() {
        val bus = BusDispatcher.create(false)
        TestBusConsumer.test(bus)
                .assertValue(false)
    }

    @Test
    fun eventsFromConsumer() {
        val bus = CompletableBusDispatcher.create<Int>()
        val consumer = object : TestBusConsumer<Int>() {

            private var hasEvent: Boolean = false

            override fun onEvent(event: Int) {
                if (!hasEvent) {
                    super.onEvent(event)
                    hasEvent = true
                    bus.onEvent(TEST_VALUE)
                } else {
                    super.onEvent(event)
                    bus.onError(Throwable())
                }
            }
        }
        bus.listen(consumer)
        bus.onEvent(0)

        consumer.assertValues(0, TEST_VALUE)
                .assertError(Throwable::class.java)
                .assertNotCompleted()
    }

    @Test
    fun listenFromConsumer() {
        val bus = BusDispatcher.create<Int>()
        bus.listen {
            TestBusConsumer.test(bus)
                    .assertValue(TEST_VALUE)
        }
        bus.onEvent(TEST_VALUE)
    }

    @Test
    fun error() {
        val bus = CompletableBusDispatcher.create<Any>()
        val exception = IllegalStateException()
        bus.onError(exception)
        TestBusConsumer.test(bus).assertError(exception)
    }

    @Test
    fun errorInSynchronousEndlessBus() {
        val bus = BusDispatcher.create<Int>()
        bus.filter { integer -> integer == 0 }
                .distinctUntilChanged { _ -> throw IllegalStateException() }
                .listen { fail() }

        try {
            bus.onEvent(0)
            fail()
        } catch (ignored: EndlessBusCompletedException) {
        }

    }

    @Test
    fun errorIsTerminal() {
        val bus = CompletableBusDispatcher.create<Int>()

        bus.onError(Exception())

        // todo: do we expect contract violation exception here?
        bus.onEvent(1)
        bus.onComplete()

        TestBusConsumer.test(bus)
                .assertError(Exception::class.java)
                .assertNoValues()
    }

    @Test
    fun asyncErrorIsTerminal() {
        // in case we do have observeOn for completable bus,
        // just cast as for now
        val publisher = CompletableBusDispatcher.create<Int>()
        val bus = publisher.observeOn(singleThreaded())

        publisher.onError(Exception())
        publisher.onEvent(1)
        publisher.onComplete()

        TestBusConsumer.test(bus)
                .awaitTerminalEvent()
                .assertError(Exception::class.java)
                .assertNoValues()
    }

    @Test
    fun completeIsTerminal() {
        val bus = CompletableBusDispatcher.create<Int>()

        bus.onComplete()
        bus.onEvent(1)
        bus.onError(Exception())

        TestBusConsumer.test(bus)
                .assertCompleted()
                .assertNoValues()
    }

    @Test
    @Throws(InterruptedException::class)
    fun contractCheck() {
        val latch = CountDownLatch(1)
        val bus = CompletableBusDispatcher.create<Int>()
        val exception = AtomicReference<Exception>()
        val errorHappened = AtomicReference(false)
        bus.doOnEvent {
            try {
                Thread.sleep(100)
            } catch (e: Exception) {
                exception.set(e)
            }
        }.listen({ fail("onEvent after onError") }, { errorHappened.set(true) })

        Thread {
            latch.countDown()
            bus.onEvent(123)
        }.start()
        latch.await()
        bus.onError(IllegalStateException())
        Thread.sleep(200)
        if (exception.get() != null) {
            fail("thread interrupted")
        }
        if (!errorHappened.get()) {
            fail("error not happened")
        }
    }

    @Test
    fun customBusChains() {
        val dispatcher = Bus.create<Int> { consumer ->
            consumer.onEvent(1)
            nonCancellable()
        }

        val map = dispatcher.map { int -> int > 0 }

        val test = TestBusConsumer<Boolean>()
        map.listen(test)

        test.assertValue(true)

        map.listen(test)
        test.assertValue(true)
    }
}