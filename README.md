# What is this?

An experiment on the idea of Google's Dagger using different means of code generation: Kotlin compiler.

I don't see any particular benefit from doing it this way, but it is always nice to entertain the idea.

It is a personal project to make myself more familiar with the Kotlin compiler internals and code 
generation in general.

If you, for any reason, want to look through this:

- The plugin files are in: `buildSrc/compiler-plugin/kotlin-plugin` (this is what attaches to compiler)
- The test project files is in `src/main/kotlin` (this is what gets compiled)

Right now I have implemented only the concept of exposing dependencies through `Component` and providing
them through `Module` implemented as `object`.

# To launch test file

Use `./gradlew run`. It will build and install plugin to maven local, after it will compile the test project
using freshly built plugin and run it. Yay!
