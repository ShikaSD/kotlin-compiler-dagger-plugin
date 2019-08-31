The main topic of this note is to explore robust DI compile-time "safe" container implementation relying on language itself, not annotation processing.

I see several main points of implementation for MVP:
1. Exposing dependencies from container
2. Wiring(binding) dependency graph
3. Injection (constructor and instance fields)
4. Scoping (local singletons)
5. Subscoping and providing parts of the parent graph to children.

## Exposing dependencies:
The container providing dependencies can be called a `Component` in analogy with dagger.
To associate each graph with correct instance calls, we can use a named component interface. To distinguish those (and to go away from annotations `TODO: why?`), marker `interface Component` can be used.

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
fun <T: Component> component(vararg dependencies: Any?): T = TODO()

// Client
fun init() {
    val fooComponent = component<FooComponent>(
        bind<Bar>(barInstance),
        bind<Bar1>(::bar1Provider)
    )

    val instance: Bar = fooComponent.get()
}
```

It should be possible to generate the endpoints dynamically based on the usage in code. There is a problem of implementation details implicitly escaping interface of DI container, so it is questionable direction to proceed.

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
