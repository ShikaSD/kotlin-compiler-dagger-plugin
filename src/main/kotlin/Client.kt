import lib.Component
import lib.bind
import lib.component
import lib.get

interface Main : Component

// Generated
fun Main.getInt(): Int = 0

fun main() {
    val intInstance = 0

    val componentImpl = component<Main>(
        bind(intInstance),
        bind { it: Int -> it.toString() }
    )

    println(componentImpl.get<Int>())
    println(componentImpl.get<String>())
}
