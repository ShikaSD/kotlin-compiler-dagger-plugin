import dagger.Component
import dagger.Module
import dagger.Provides
import javax.inject.Inject
import javax.inject.Scope

@TestScope
@Component(modules = [TestModule::class, TestModuleInstance::class])
interface Main {
    fun provider(): String
    fun providerLambda(): () -> String
    fun injected(): Injected
    fun scopedInjected(): ScopedInjected
    fun inject(instance: Injected2)
}

@Module
object TestModule {
    @Provides
    fun string(int: Int): String = int.toString()

    @TestScope
    @Provides
    fun lambdaString(int: Int, string: String): () -> String = { int.toString() + string }.also { println("Invoked lambdaString") }

    @Provides
    fun lambdaInt(int: Int): () -> Int = { int }
}

class Injected @Inject constructor(val lambda: () -> String) {
    init {
        println("Created injected")
    }
}

@TestScope
class ScopedInjected @Inject constructor(val lambda: () -> String) {
    init {
        println("Created scoped injected")
    }
}

class Injected2 {
    @Inject
    lateinit var lambdaString: () -> String
    @Inject
    var int: Int = 0
    @Inject
    lateinit var lambdaInt: () -> Int
    @Inject
    fun setString(string: String) {
        println("Have set $string")
    }
}

@Module
class TestModuleInstance(val int: Int) {
    @TestScope
    @Provides
    fun int(): Int = int.also { println("Invoked int") }
}

@Scope
annotation class TestScope

fun main(args: Array<String>) {
    val component = Main.Component(TestModuleInstance(3))
    println(component.injected().lambda())
    println(component.injected().lambda())
    println(component.scopedInjected().lambda())
    println(component.scopedInjected().lambda())

    println(component.providerLambda()())
    val injected2 = Injected2()
    component.inject(injected2)
    println(injected2.lambdaString())
}
