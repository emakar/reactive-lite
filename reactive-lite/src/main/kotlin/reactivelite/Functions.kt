@file:Suppress("unused") // public api

package reactivelite

typealias Callable<T> = () -> T

typealias Func0<R>              = () -> R
typealias Func1<T, R>           = (T) -> R
typealias Func2<T1, T2, R>      = (T1, T2) -> R
typealias Func3<T1, T2, T3, R>  = (T1, T2, T3) -> R

typealias Action0               = () -> Unit
typealias Action1<T>            = (T) -> Unit
typealias Action2<T1, T2>       = (T1, T2) -> Unit
typealias Action3<T1, T2, T3>   = (T1, T2, T3) -> Unit

fun <T> emptyAction(): Actions.EmptyAction<T> {
    @Suppress("UNCHECKED_CAST") // no-op actively has no casts
    return Actions.EMPTY_ACTION as Actions.EmptyAction<T>
}

fun emptyAction0(): Actions.EmptyAction<*> {
    return Actions.EMPTY_ACTION
}

fun nonCancellable(): Cancellable {
    return Actions.EMPTY_ACTION
}

object Actions {

    val EMPTY_ACTION = EmptyAction<Any>()

    // todo internal
    class EmptyAction<in T> : Action0, Action1<T>, Action2<T, T>, Action3<T, T, T> {
        override fun invoke() {}
        override fun invoke(p1: T) {}
        override fun invoke(p1: T, p2: T) {}
        override fun invoke(p1: T, p2: T, p3: T) {}
    }
}
