
interface DoThing {
    fun doIt()
}

class Instance : Extension<Int>, DoThing {
    override fun doIt() {

    }
}

fun thing(doThing: DoThing) {
    doThing.doIt()
}

fun main() {
    Instance1()
    0.doIt() // Should compile
    0.kek() // Should compile
    thing(0)
}
