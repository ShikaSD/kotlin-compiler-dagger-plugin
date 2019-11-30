# What is this?

An experiment on the idea of Google's Dagger using different means of code generation: Kotlin compiler.

For now I continue working on implementing richer feature-set. Most of the code is generated in frontend using KotlinPoet.
IDE support works out of the box (at least for JB products) thanks to gradle integration.
As a side effect of this project, I am [exploring](https://github.com/ShikaSD/kotlin-compiler-di/blob/kotlin-syntax-experiment/PROPOSAL.md) how DI can be done better using Kotlin (in compile time safe way).

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
- provide external dependencies using `@Factory`
- Support of `@BindsInstance` for components
- Support for `@Qualifier`
- external dependencies through `@Builder`
- Type mapping using `@Binds`
- `Lazy` and `Provider` support

TODO (in any order):
- Default builder
- `IntoSet` / `IntoMap`
- Subcomponents
- Proper scope support
- ... the rest

# To launch test file

Use `./gradlew run`. It will build and install plugin to maven local, after it will compile the test project
using freshly built plugin and run it. Yay!
