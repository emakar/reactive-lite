package reactivelite.operators

import org.junit.After
import org.junit.Assert
import org.junit.Test
import reactivelite.*
import reactivelite.utils.SpyErrorHandler
import reactivelite.utils.singleThreaded
import java.util.concurrent.TimeUnit

class TaskObserveOnTest {

    @After
    fun resetErrorHandler() {
        SpyErrorHandler.reset()
    }

    @Test
    fun observeOn() {
        val task = Task.just(true)
                .observeOn(singleThreaded())

        val lastSeenThread = TestTaskConsumer.test(task)
                .awaitTerminalEvent(50, TimeUnit.MILLISECONDS)
                .lastSeenThread

        Assert.assertNotEquals(Thread.currentThread(), lastSeenThread)
    }

    @Test
    fun errorBeforeObserveOn() {
        val task = Task.just(true)
                .map { throw IllegalStateException() }
                .observeOn(singleThreaded())

        val lastSeenThread = TestTaskConsumer.test(task)
                .awaitTerminalEvent(50, TimeUnit.MILLISECONDS)
                .assertError(IllegalStateException::class.java)
                .lastSeenThread

        Assert.assertNotEquals(Thread.currentThread(), lastSeenThread)
    }

    @Test
    fun errorAfterObserveOn() {
        val task = Task.just(true)
                .observeOn(singleThreaded())
                .map { throw IllegalStateException() }

        val lastSeenThread = TestTaskConsumer.test(task)
                .awaitTerminalEvent(50, TimeUnit.MILLISECONDS)
                .assertError(IllegalStateException::class.java)
                .lastSeenThread

        Assert.assertNotEquals(Thread.currentThread(), lastSeenThread)
    }

    @Test
    fun fatalErrorObserveOn() {
        val spyErrorHandler = SpyErrorHandler.makeDefault()
        Task.just(true)
                .observeOn(singleThreaded())
                .map { throw IllegalStateException() }
                .start()

        Thread.sleep(300)
        spyErrorHandler.assertExceptions(OnErrorNotImplementedException::class.java)
    }
}
