package lib

interface Component

interface Binding<T>

fun <T> Component.get(): T = TODO("Should be generated")

fun <T : Component> component(vararg dependencies: Binding<*>): T = TODO("Should be generated")

fun <T> bind(instance: T): Binding<T> = TODO("Should be generated")

fun <A, B> bind(instanceProvider: (A) -> B): Binding<B> = TODO("Should be generated")
