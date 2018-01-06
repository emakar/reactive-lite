package reactivelite

import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import reactivelite.utils.singleThreaded
import java.util.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class BusCancellationTest {

    @Test
    fun cancel() {
        val bus = BusDispatcher.create<Boolean>()

        val consumer = TestBusConsumer<Boolean>()
        val cancellable = bus.listen(consumer)

        bus.onEvent(true)
        cancellable.cancel()
        bus.onEvent(false)

        consumer.assertValues(true)
                .assertNoErrors()
                .assertNotCompleted()
    }

    @Test
    fun hotAliveWhileSilent() {
        val publisher = BusDispatcher.create(123)

        val bus = publisher
                .distinctUntilChanged()

        // distinctUntilChanged won't receive it now,
        // but it's ok since it synchronously receives latest from upstream once being listened
        publisher.onEvent(-3)

        bus.listen({ }).cancel()

        publisher.onEvent(2)
        publisher.onEvent(3)

        TestBusConsumer.test(bus)
                .awaitValueCount(1, 100, TimeUnit.MILLISECONDS)
                .assertValues(3)
                .assertNotCompleted()
                .assertNoErrors()
    }

    @Test
    fun hotObserveOnAliveWhileSilent() {
        val publisher = BusDispatcher.create(123)

        val bus = publisher
                .observeOn(singleThreaded())

        // observeOn won't receive it now,
        // it will receive latest on being listened (but asynchronously)
        publisher.onEvent(-3)

        // here observeOn starts receiving -3 asynchronously
        bus.listen({ }).cancel()

        // and here it is cancelled ('disconnected') ant won't receive anymore
        publisher.onEvent(2)
        publisher.onEvent(3)

        // listening starts again, and observeOn still holds '-3' as latest value
        TestBusConsumer.test(bus)
                .awaitValueCount(1, 100, TimeUnit.MILLISECONDS)
                .assertValues(3)
                .assertNotCompleted()
                .assertNoErrors()
    }

    @Test
    fun cancelFromConsumer() {
        val bus = BusDispatcher.create(1)

        val cancellableHolder = AtomicReference<Cancellable>()
        val hits = AtomicInteger()
        val latch = CountDownLatch(1)
        val latch2 = CountDownLatch(1)

        val cancellable = bus
                .observeOn(singleThreaded())
                .listen {
                    latch2.await()
                    latch.countDown()
                    hits.incrementAndGet()
                    val cancellable = cancellableHolder.get()
                    cancellable?.cancel()
                }

        latch2.countDown()
        cancellableHolder.set(cancellable)

        latch.await()

        bus.onEvent(2)

        Thread.sleep(100)

        assertEquals(1, hits.get().toLong())
    }

    @Test
    fun cancelFromAnotherConsumer() {
        // here we emulate looping so that it's possible for the same thread
        // to execute some observed actions later, and ensure happens-before behavior
        val executor = Executors.newSingleThreadExecutor()

        // collect 'messages' to proceed with later
        val messages = LinkedList<Action0>()
        val scheduler: Scheduler = { action ->
            messages.add(action)
            nonCancellable()
        }

        val firstExecutionLatch = CountDownLatch(1)
        val exception = AtomicReference<Exception>()

        executor.execute {
            try {
                doCancelFromAnotherConsumer(scheduler)
            } catch (e: Exception) {
                exception.set(e)
            } finally {
                firstExecutionLatch.countDown()
            }
        }

        firstExecutionLatch.await()
        var e: Exception? = exception.get()
        if (e != null) {
            throw e
        }

        val scheduleLatch = CountDownLatch(1)
        val scheduleException = AtomicReference<Exception>()
        executor.execute {
            try {
                for (message in messages) {
                    message.invoke()
                }
            } catch (messageE: Exception) {
                scheduleException.set(messageE)
            } finally {
                scheduleLatch.countDown()
            }
        }

        scheduleLatch.await()
        e = scheduleException.get()
        if (e != null) {
            throw e
        }
    }

    private fun doCancelFromAnotherConsumer(scheduler: Scheduler) {
        val bus = BusDispatcher.create(1)

        val cancellableHolder = arrayOfNulls<Cancellable>(2)

        cancellableHolder[0] = bus
                .observeOn(scheduler)
                .listen { cancellableHolder[1]!!.cancel() }

        cancellableHolder[1] = bus
                .observeOn(scheduler)
                .listen { fail() }
    }

    @Test
    fun lockAroundBus() {
        val bus = BusDispatcher.create<Int>()
        val testLock = Any()
        val cancellable = bus.listen({ synchronized(testLock) {} })

        val exception = AtomicReference<Exception>()
        val latch = CountDownLatch(1)

        Thread {
            synchronized(testLock) {
                try {
                    Thread.sleep(150)
                    cancellable.cancel()
                    bus.listen({ })
                } catch (messageE: Exception) {
                    exception.set(messageE)
                } finally {
                    latch.countDown()
                }
            }
        }.start()
        Thread.sleep(50)
        bus.onEvent(123)
        latch.await()
        val e = exception.get()
        if (e != null) {
            throw e
        }
    }
}