package reactivelite

import org.junit.Assert.assertTrue
import org.junit.Test

class SignalTest {

    @Test
    fun smoke() {
        TestSignalConsumer.test(Signal.signal())
                .assertSignal()

        TestSignalConsumer.test(Signal.error(IllegalStateException()))
                .assertError(IllegalStateException::class.java)
    }

    @Test
    fun fromTask() {
        val testVal = 123

        val task = Task.create<Int> { consumer ->
            consumer.onSuccess(testVal)
            nonCancellable()
        }

        var succeeded = false

        Signal.fromTask(task).start { succeeded = true }
        assertTrue(succeeded)

        Signal.fromTask(task).start({ succeeded = true }, { succeeded = false })
        assertTrue(succeeded)
    }
}
