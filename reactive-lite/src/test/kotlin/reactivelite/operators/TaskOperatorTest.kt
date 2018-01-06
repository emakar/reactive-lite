package reactivelite.operators

import org.junit.Assert
import org.junit.Test
import reactivelite.*
import reactivelite.utils.TEST_VALUE
import java.util.concurrent.atomic.AtomicBoolean

class TaskOperatorTest {

    @Test
    fun map() {
        val task = Task.just(true)
                .map { TEST_VALUE }

        TestTaskConsumer.test(task)
                .assertSuccess(TEST_VALUE)
    }

    @Test
    fun doOnSuccess() {
        val doOnSuccessValue = AtomicBoolean(false)
        val task = Task.just(true)
                .doOnSuccess { doOnSuccessValue.set(true) }

        TestTaskConsumer.test(task)
                .assertSuccess(true)

        Assert.assertTrue(doOnSuccessValue.get())
    }

    @Test
    fun flatMap() {
        val task = Task.just(true).flatMap { Task.just<Int>(TEST_VALUE) }

        TestTaskConsumer.test(task)
                .assertSuccess(TEST_VALUE)
    }
}
