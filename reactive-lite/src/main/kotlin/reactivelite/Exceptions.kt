@file:Suppress("unused") // public api

package reactivelite

import java.io.PrintStream
import java.io.PrintWriter
import java.util.*

// https://github.com/ReactiveX/RxJava/issues/748
// Copied from rx.exceptions.Exceptions.throwIfFatal
fun throwIfFatal(t: Throwable) {
    when (t) {
        is OnErrorNotImplementedException -> throw t
        is EndlessBusCompletedException -> throw t
        is VirtualMachineError -> throw t
        is ThreadDeath -> throw t
        is LinkageError -> throw t
        else -> { }
    }
}

fun throwIfAny(vararg exceptions: Throwable) {
    throwIfAny(Arrays.asList(*exceptions))
}

fun throwIfAny(exceptions: List<Throwable>?) {
    if (exceptions != null && !exceptions.isEmpty()) {
        throw MergedException(exceptions)
    }
}

class BusCompletedException : RuntimeException {
    constructor(message: String?) : super(message) {}
    constructor(message: String?, cause: Throwable?) : super(message, cause) {}

    companion object {
        private val serialVersionUID = -7616858294765788174L
    }
}

class BusNoValueException(message: String?) : RuntimeException(message) {
    companion object {
        private val serialVersionUID = -470699653427113740L
    }
}

class EndlessBusCompletedException : RuntimeException {
    constructor(message: String?) : super(message) {}
    constructor(message: String?, cause: Throwable?) : super(message, cause) {}

    companion object {
        private val serialVersionUID = 2846114749805588866L
    }
}

class OnErrorNotImplementedException(cause: Throwable?) : RuntimeException(cause) {
    companion object {
        private val serialVersionUID = 4334965309328963728L
    }
}

class ErrorNotDeliveredException(cause: Throwable) : RuntimeException(cause) {
    companion object {
        private val serialVersionUID = -2951069005839785370L
    }
}

class MergedException : RuntimeException {

    /**
     * Retrieves the list of exceptions that make up the `CompositeException`.
     *
     * @return the exceptions that make up the `CompositeException`, as a [List] of [Throwable]s
     */
    val exceptions: List<Throwable>
    override val message: String
    override val cause: Throwable by lazy { findCause() }

    /**
     * Constructs a CompositeException with the given array of Throwables as the
     * list of suppressed exceptions.
     * @param exceptions the Throwables to have as initially suppressed exceptions
     *
     * @throws IllegalArgumentException if `exceptions` is empty.
     */
    constructor(vararg exceptions: Throwable)
            : this(exceptions.toList())

    /**
     * Constructs a CompositeException with the given array of Throwables as the
     * list of suppressed exceptions.
     * @param errors the Throwables to have as initially suppressed exceptions
     *
     * @throws IllegalArgumentException if `errors` is empty.
     */
    constructor(errors: Iterable<Throwable>) : super() {
        val deDupedExceptions = LinkedHashSet<Throwable>()
        for (ex in errors) {
            if (ex is MergedException) {
                deDupedExceptions.addAll(ex.exceptions)
            } else {
                deDupedExceptions.add(ex)
            }
        }
        if (deDupedExceptions.isEmpty()) {
            throw IllegalArgumentException("empty errors")
        }
        this.exceptions = Collections.unmodifiableList(deDupedExceptions.toList())
        this.message = exceptions.size.toString() + " exceptions occurred. "
    }

    private fun findCause(): Throwable {
        // we lazily generate this causal chain if this is called
        val localCause = CompositeExceptionCausalChain()
        val seenCauses = HashSet<Throwable>()

        var chain: Throwable = localCause
        for (e in exceptions) {
            var last = e
            if (seenCauses.contains(last)) {
                continue
            }
            seenCauses.add(last)

            val listOfCauses = getListOfCauses(last)
            // check if any of them have been seen before
            for (child in listOfCauses) {
                if (seenCauses.contains(child)) {
                    // already seen this outer Throwable so skip
                    last = RuntimeException("Duplicate found in causal chain so cropping to prevent loop ...")
                    continue
                }
                seenCauses.add(child)
            }

            // we now have 'e' as the last in the chain
            try {
                chain.initCause(last)
            } catch (t: Throwable) {
                // ignore
                // the JavaDocs say that some Throwables (depending on how they're made) will never
                // let me call initCause without blowing up even if it returns null
            }

            chain = getRootCause(chain)
        }
        return localCause
    }

    /**
     * All of the following `printStackTrace` functionality is derived from JDK [Throwable]
     * `printStackTrace`. In particular, the `PrintStreamOrWriter` abstraction is copied wholesale.
     *
     * Changes from the official JDK implementation:
     *  * no infinite loop detection
     *  * smaller critical section holding [PrintStream] lock
     *  * explicit knowledge about the exceptions [List] that this loops through
     *
     */
    override fun printStackTrace() {
        printStackTrace(System.err)
    }

    override fun printStackTrace(s: PrintStream) {
        printStackTrace(WrappedPrintStream(s))
    }

    override fun printStackTrace(s: PrintWriter) {
        printStackTrace(WrappedPrintWriter(s))
    }

    /**
     * Special handling for printing out a `CompositeException`.
     * Loops through all inner exceptions and prints them out.
     *
     * @param s
     * stream to print to
     */
    private fun printStackTrace(s: PrintStreamOrWriter) {
        val b = StringBuilder(128)
        b.append(this).append('\n')
        for (myStackElement in stackTrace) {
            b.append("\tat ").append(myStackElement).append('\n')
        }
        var i = 1
        for (ex in exceptions) {
            b.append("  ComposedException ").append(i).append(" :\n")
            appendStackTrace(b, ex, "\t")
            i++
        }
        s.println(b.toString())
    }

    private fun appendStackTrace(b: StringBuilder, ex: Throwable, prefix: String) {
        b.append(prefix).append(ex).append('\n')
        for (stackElement in ex.stackTrace) {
            b.append("\t\tat ").append(stackElement).append('\n')
        }
        val cause = ex.cause
        if (cause != null) {
            b.append("\tCaused by: ")
            appendStackTrace(b, cause, "")
        }
    }

    internal abstract class PrintStreamOrWriter {
        /** Prints the specified string as a line on this StreamOrWriter.  */
        internal abstract fun println(o: Any)
    }

    /**
     * Same abstraction and implementation as in JDK to allow PrintStream and PrintWriter to share implementation.
     */
    private class WrappedPrintStream internal constructor(private val printStream: PrintStream) : PrintStreamOrWriter() {

        override fun println(o: Any) {
            printStream.println(o)
        }
    }

    private class WrappedPrintWriter internal constructor(private val printWriter: PrintWriter) : PrintStreamOrWriter() {

        override fun println(o: Any) {
            printWriter.println(o)
        }
    }

    internal class CompositeExceptionCausalChain : RuntimeException(MESSAGE) {
        companion object {
            private val serialVersionUID = 3875212506787802066L
            val MESSAGE = "Chain of Causes for CompositeException In Order Received =>"
        }
    }

    private fun getListOfCauses(ex: Throwable): List<Throwable> {
        val list = ArrayList<Throwable>()
        val exCause: Throwable? = ex.cause
        if (exCause == null || exCause == ex) {
            return list
        }

        var root: Throwable = exCause
        while (true) {
            list.add(root)
            val cause = root.cause
            if (cause == null || cause == root) {
                return list
            }
            root = cause
        }
    }

    /**
     * Returns the number of suppressed exceptions.
     * @return the number of suppressed exceptions
     */
    fun size(): Int {
        return exceptions.size
    }

    /**
     * Returns the root cause of `e`. If `e.getCause()` returns `null` or `e`, just return `e` itself.
     *
     * @param e the [Throwable] `e`.
     * @return The root cause of `e`. If `e.getCause()` returns `null` or `e`, just return `e` itself.
     */
    private fun getRootCause(e: Throwable): Throwable {
        var root: Throwable? = e.cause
        if (root == null || root == cause) {
            return e
        }
        while (true) {
            val cause = root!!.cause
            if (cause == null || cause == root) {
                return root
            }
            root = cause
        }
    }

    companion object {

        private val serialVersionUID = -215716629097774271L
    }
}