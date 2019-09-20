This note explores compile-time "safe" DI container implementation relying on language itself, as opposed to annotation processing.

The topics to explore for MVP:
1. Exposing dependencies from container
2. Wiring(binding) dependency graph
3. Injection (constructor and instance fields)
4. Scoping (local singletons)
5. Subscoping and providing parts of the parent graph to children.

## Exposing dependencies:
The problem of exposing dependencies can be reduced to constructor injection.
Similarly to dagger, which is generating implementation for an interface, we can imagine the container for 
exposed types as a class. 
The framework task in this scenario is to wire chain of the constructors with correct dependencies.
```kotlin
class FooComponent(
    val bar: Bar,
    val otherDependency: Other.Dependency
)

// or

interface FooComponent {
    val bar: Bar
    val otherDependency: Other.Dependency
}
```
Component definition can be done using Kotlin DSL and compiler transformations. `TODO: provide more details`
```kotlin
// Library
fun <T> component(vararg dependencies: Binding<*>): T = TODO()
fun module(definition: () -> Array<Binding<*>>): Array<Binding>

// Client
fun init() {
    val fooComponent = component<FooComponent>(
        bind<Bar>(barInstance),
        bind<Bar1>(::bar1Provider),
        *module(::fooBarModule)
    )

    val instance: Bar = fooComponent.bar
}
```
This way, the dependency graph can be defined in multiple ways. The framework task is to ensure that
all the types are known in the compile time and validate the graph. 

Food for thought:
 - Is such "dynamic" definition providing better user experience rather than "static graph" that Dagger use?

## Wiring

`TODO: module definition`

`TODO: explore macwire`

`TODO: explore boost di`

## Injection

`TODO: more details`

Field injection:
```kotlin
// Library
interface Injectable<T> {
    fun <R> inject(): ReadOnlyProperty<Injectable<T>, R> = // delegate
}

// Client 
class SomeClass : Injectable<Foo> {
    val someField: Foo by inject() // Can we make it compile time safe?
}
```

## Scoping

`TODO: local scoping and their relation`

## Subscoping

`TODO: parent - children relationship`
