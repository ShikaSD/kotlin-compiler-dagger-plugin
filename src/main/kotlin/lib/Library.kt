package lib

fun <T> component(vararg dependencies: Binding<*>): T = TODO("Should be generated")

interface Binding<T>
interface Instance<T> : Binding<T>
interface Provider<T, R> : Binding<T>

fun <T> bind(t: T): T = TODO()
fun <T> bind(): Binding<T> = TODO()
infix fun <T, R> Binding<T>.with(factory: R): Provider<T, R> = TODO()
typealias Factory<T> = () -> T
