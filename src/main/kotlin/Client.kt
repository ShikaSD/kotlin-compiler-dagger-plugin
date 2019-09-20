import lib.Module
import lib.bind
import lib.component
import lib.get
import lib.module
import lib.with

fun main() {
    val foo = component<Foo>(
        ::Bar,
        1,
        { it: Int -> it.toString() }
    )

    val foo1 = component<Foo>(
        0,
        { it: Int -> it.toString() }
    )

    println(foo)
    println(foo1)

    component<Foo>(
        bind<Int>() with { int: Int ->  }
    )
}

data class Foo(
    val bar: Bar,
    val int: Int,
    val string: String
)

data class Bar(val int: Int)
