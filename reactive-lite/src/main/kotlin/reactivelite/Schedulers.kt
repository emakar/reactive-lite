@file:Suppress("unused") // public api

package reactivelite

import java.util.concurrent.ExecutorService

typealias Scheduler = (() -> Unit) -> Cancellable

fun Scheduler.schedule(action: () -> Unit) = this.invoke(action)

fun fromExecutor(executor: ExecutorService, interruptOnCancel: Boolean = false): Scheduler {
    return ExecutorScheduler(executor, interruptOnCancel)
}

private class ExecutorScheduler(private val executor: ExecutorService, private val interruptOnCancel: Boolean) : Scheduler {

    override fun invoke(action: () -> Unit): Cancellable {
        val pendingAction = executor.submit(action)
        return {
            pendingAction.cancel(interruptOnCancel)
        }
    }
}
