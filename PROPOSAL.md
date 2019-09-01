This note explores compile-time "safe" DI container implementation relying on language itself, as opposed to annotation processing.

The topics to explore for MVP:
1. Exposing dependencies from container
2. Wiring(binding) dependency graph
3. Injection (constructor and instance fields)
4. Scoping (local singletons)
5. Subscoping and providing parts of the parent graph to children.

## Exposing dependencies:
The container providing dependencies called a `Component` in analogy with dagger.
To associate each graph with correct instance calls, we can use a named component interface.
To distinguish those (and to go away from annotations `TODO: why?`), marker `interface Component` can be used.

Kotlin has extension functions which can be leveraged here, providing a single entry point for every call.
```kotlin
// Base function defined in library
fun <T> Component.get(): T = TODO("Should be generated")

// Definition of component in the client code
interface FooComponent : Component

// Generated functions for each endpoint
fun FooComponent.getBar(): Bar = ...
```
Base function will be substituted for generated one at compile time. 

Component definition can be done using Kotlin DSL and compiler transformations. `TODO: provide more details`
```kotlin
// Library
fun <T: Component> component(vararg dependencies: Binding<*>): T = TODO()
fun module(definition: () -> Array<Binding<*>>): Array<Binding>

// Client
fun init() {
    val fooComponent = component<FooComponent>(
        bind<Bar>(barInstance),
        bind<Bar1>(::bar1Provider),
        *module(::fooBarModule)
    )

    val instance: Bar = fooComponent.get()
}
```

It should be possible to generate the endpoints dynamically based on the usage in code.
Problems to address:
 - Runtime branching in parameters
 - implementation details implicitly escaping interface of DI container.

## Wiring

`TODO: module definition`
`TODO: check macwire for examples`

## Injection

`TODO: more details`

Field injection:
```kotlin
// Library
interface Injectable<T : Component> {
    fun <R> inject(): ReadOnlyProperty<Injectable<T>, R> = // delegate
}

// Client 
class SomeClass : Injectable<FooComponent> {
    val someField: Foo by inject() // Can we make it compile time safe?
}
```

## Scoping

`TODO: local scoping and their relation`

## Subscoping

`TODO: parent - children relationship`
