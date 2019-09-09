import lib.component

fun main() {
    val foo = component<Foo>(
        ::Bar,
        0,
        { it: Int -> it.toString() }
    )
}

data class Foo(
    val bar: Bar,
    val int: Int,
    val string: String
)

data class Bar(val int: Int)

fun bar(): Int = 0
