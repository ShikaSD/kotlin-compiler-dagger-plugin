import lib.*

interface Main : Component

interface Dependency {
    fun longInstance(): Long
}

fun main() {
    val intInstance = 0

    val componentImpl = component<Main>(
        bind(intInstance),
        module(
            bind { it: Int -> it.toString() }
        ),
        dependency(
            object : Dependency {
                override fun longInstance(): Long = 1L
            }
        )
    )

    println(componentImpl.get<Int>())
    println(componentImpl.get<String>())
    println(componentImpl.get<Long>())
}
