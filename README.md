# What is this?

An experiment on the idea of Google's Dagger using different means of code generation: Kotlin compiler.

It is a personal project to make myself more familiar with the Kotlin compiler internals and code 
generation in general.

For now I continue working on implementing richer feature-set. Most of the code is generated in backend using IR generation.
I am also exploring better Kotlin API (probably can borrow something from Koin or Kodein).

If you, for any reason, want to look through this:

- The plugin files are in: `buildSrc/compiler-plugin/kotlin-plugin` (this is what attaches to compiler)
- The test project files is in `src/main/kotlin` (this is what gets compiled)

Right now I have implemented the concept of:
- exposing dependencies through `Component`.
- providing them through `Module` implemented as `object` or `class` instance.
- providing using `@Inject` annotated constructor
- inject dependencies into `@Inject` annotated fields
- inject dependencies into `@Inject` annotated functions (one param only)
- local scoping inside component using `@Scope` annotations

TODO (in any order):
- Component dependencies
- Support of `@BindsInstance` for components
- `Qualifier`
- `Lazy` and `Provider` support
- Subcomponents
- ... the rest

# To launch test file

Use `./gradlew run`. It will build and install plugin to maven local, after it will compile the test project
using freshly built plugin and run it. Yay!
