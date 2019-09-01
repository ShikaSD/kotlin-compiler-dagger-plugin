package lib

interface Component

interface Binding
interface Container : Binding
interface Instance<T> : Binding

fun <T> Component.get(): T = TODO("Should be generated")
fun <T : Component> component(vararg dependencies: Binding): T = TODO("Should be generated")

fun module(vararg dependencies: Binding): Container = TODO()
fun <T> dependency(value: T): Container = TODO()
fun <T> bind(instance: T): Instance<T> = TODO()
fun <A, B> bind(instanceProvider: (A) -> B): Instance<B> = TODO()
