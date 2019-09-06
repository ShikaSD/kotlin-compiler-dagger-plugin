import lib.component

fun main() {
    val foo = component<Foo>(
        0,
        ""
    )
    println(foo)
}

data class Foo(
    val int: Int,
    val string: String
)
