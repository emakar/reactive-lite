package reactivelite.utils

import org.junit.Assert.*
import reactivelite.Action1
import reactivelite.FallbackErrorHandler
import reactivelite.MergedException
import java.util.*

class SpyErrorHandler private constructor() {

    private val fallbackHandler: Action1<Throwable>
    private val uncaughtExceptionHandler: Thread.UncaughtExceptionHandler
    @Volatile private var throwable: Throwable? = null

    init {
        fallbackHandler = { throwable ->
            this.throwable = throwable
            // FallbackErrorHandler redelivers to uncaught handler,
            // so test default behavior
            sneakyThrow<RuntimeException>(throwable)
        }
        uncaughtExceptionHandler = Thread.UncaughtExceptionHandler { _, e ->
            val throwable = this.throwable
            // we are spying, but could not be handled with FallbackErrorHandler
            if (throwable != null) {
                assertEquals(throwable, e)
            }
        }
    }

    fun assertExceptions(vararg expected: Throwable) {
        val throwable = this.throwable
        if (throwable is MergedException) {
            val actual = throwable.flatten().toList()
            assertTrue(actual.containsAll(Arrays.asList(*expected)))
            return
        }

        assertEquals(1, expected.size.toLong())
        val t = expected[0]
        assertEquals(t, throwable)
    }

    @SafeVarargs
    fun assertExceptions(vararg expected: Class<out Throwable>) {
        val throwable = this.throwable
        assertNotNull(throwable)
        if (throwable is MergedException) {

            val actual = throwable.flatten()
                    .map { it.javaClass }
                    .toList()

            assertTrue(actual.containsAll(Arrays.asList(*expected)))
            return
        }

        assertEquals(1, expected.size.toLong())
        val tClass = expected[0]
        assertEquals(tClass, throwable!!.javaClass)
    }

    // include both origins and causes since some exceptions could wrap several times
    // e.g. EndlessBusCompletedException and others
    private fun MergedException.flatten(): Iterable<Throwable> {
        return this.exceptions
                .flatMap { t ->
                    val cause = t.cause
                    if (cause != null)
                        listOf<Throwable>(t, cause)
                    else
                        listOf(t)
                }
    }

    companion object {

        private var originalUncaughtExceptionHandler: Thread.UncaughtExceptionHandler? = null
        private var spying: Boolean = false

        @Synchronized
        fun makeDefault(): SpyErrorHandler {
            spying = true
            originalUncaughtExceptionHandler = Thread.getDefaultUncaughtExceptionHandler()

            val errorHandler = SpyErrorHandler()
            FallbackErrorHandler.set(errorHandler.fallbackHandler)
            Thread.setDefaultUncaughtExceptionHandler(errorHandler.uncaughtExceptionHandler)
            return errorHandler
        }

        @Synchronized
        fun reset() {
            if (!spying) {
                return
            }
            FallbackErrorHandler.set(null)
            Thread.setDefaultUncaughtExceptionHandler(originalUncaughtExceptionHandler)
            originalUncaughtExceptionHandler = null
            spying = false
        }

        private fun <T : Throwable> sneakyThrow(throwable: Throwable) {
            throw throwable
        }
    }
}
