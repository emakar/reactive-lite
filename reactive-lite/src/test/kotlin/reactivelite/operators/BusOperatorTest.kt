package reactivelite.operators

import org.junit.Assert.assertTrue
import org.junit.Test
import reactivelite.*

class BusOperatorTest {

    @Test
    fun filter() {
        val publisher = BusDispatcher.create(0)
        val bus = publisher.filter { integer -> integer % 2 == 0 }

        val consumer = TestBusConsumer.test(bus)

        publisher.onEvent(1)
        consumer.assertValue(0)
        publisher.onEvent(2)
        consumer.assertValue(2)
    }

    @Test
    fun distinctUntilChanged() {
        val publisher = BusDispatcher.create(0)
        val bus = publisher.distinctUntilChanged { integer -> integer % 2 == 0 }
        val consumer = TestBusConsumer.test(bus)
        publisher.onEvent(2)
        publisher.onEvent(1)
        publisher.onEvent(4)
        publisher.onEvent(3)
        publisher.onEvent(1)
        consumer.assertValues(0, 1, 4, 3)
    }

    @Test
    fun distinctUntilChangedStateless() {
        val publisher = BusDispatcher.create<Int>()
        val bus = publisher.distinctUntilChanged()
        val consumer1 = TestBusConsumer.test(bus)
        val consumer2 = TestBusConsumer.test(bus)

        publisher.onEvent(1)
        consumer1.assertValue(1)
        consumer2.assertValue(1)
    }

    @Test
    fun map() {
        var cancelled = false

        val bus = CompletableBus.create<Boolean> { consumer ->
            consumer.onEvent(true)
            return@create {
                cancelled = true
            }
        }

        val map = bus.map { bool ->
            if (bool) {
                123
            } else {
                234
            }
        }

        val consumer = TestBusConsumer<Int>()
        val cancellable = map.listen(consumer)
        consumer.assertValue(123)

        cancellable.cancel()
        assertTrue(cancelled)
    }
}