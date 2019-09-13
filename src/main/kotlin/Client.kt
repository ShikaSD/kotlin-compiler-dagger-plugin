import lib.component

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
}

data class Foo(
    val bar: Bar,
    val int: Int,
    val string: String
)

data class Bar(val int: Int)

fun bar(): Int = 0


fun qwe() {

//    component<Foo>(
//        bind<Int>(0),
//        bindF<Bar> { foo: Foo, s: String -> Bar(foo, s) },
//        bindF<String>(::lol)
//    )
}

fun <T> bind(t: T): T = TODO()
fun <T> bindF(r: Any?): T = TODO()
