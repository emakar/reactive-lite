package reactivelite.operators

import org.junit.Assert.assertNotEquals
import org.junit.Test
import reactivelite.*
import reactivelite.utils.singleThreaded
import java.util.concurrent.TimeUnit

class BusConcatTest {

    @Test
    fun concatInitialValue() {
        val bus = Bus.concat(0, Bus.concat(1, BusDispatcher.create(2)))
        TestBusConsumer.test(bus)
                .assertValues(0, 1, 2)
    }

    @Test
    fun innerValuesNotChanged() {
        val inner = BusDispatcher.create(true)

        val bus = Bus.concat(false, inner)
        bus.listen({ })

        TestBusConsumer.test(inner)
                .assertValue(true)
    }

    @Test
    fun innerValuesNotChangedForCompletable() {
        val inner = CompletableBusDispatcher.create(true)

        val bus = CompletableBus.concat(false, inner)

        bus.listen(emptyAction(), emptyAction(), emptyAction0())

        TestBusConsumer.test(inner)
                .assertValue(true)
    }

    @Test
    fun outerOperatorWorks() {
        val bus = Bus.concat(0, BusDispatcher.create(1).filter { false })
                .observeOn(singleThreaded())

        val thread = TestBusConsumer.test(bus)
                .awaitValueCount(1, 100, TimeUnit.MILLISECONDS)
                .assertValues(0)
                .lastSeenThread

        assertNotEquals(Thread.currentThread(), thread)
    }
}
