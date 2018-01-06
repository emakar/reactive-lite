package reactivelite

class FallbackErrorHandler private constructor() {

    init {
        throw AssertionError()
    }

    companion object {

        @Volatile private var errorHandler: Action1<Throwable>? = null

        fun set(errorHandler: Action1<Throwable>?) {
            Companion.errorHandler = errorHandler
        }

        fun handle(t: Throwable) {
            val handler = errorHandler
            if (handler == null) {
                signalUncaught(t)
                return
            }

            try {
                handler.invoke(t)
            } catch (handlerException: Throwable) {
                signalUncaught(handlerException)
            }

        }

        private fun signalUncaught(t: Throwable) {
            val current = Thread.currentThread()
            val handler = current.uncaughtExceptionHandler
            handler.uncaughtException(current, t)
        }
    }
}
