import dagger.Component
import dagger.Module
import dagger.Provides
import java.io.File
import javax.inject.Inject
import javax.inject.Scope

@TestScope
@Component(
    modules = [TestModuleInstance::class, TestModule::class, AbstractModule::class],
    dependencies = [Dependency::class]
)
interface Main : Common {
    @Component.Factory
    interface Factory {
        fun build(
            dependency: Dependency,
            testModuleInstance: TestModuleInstance
//            @BindsInstance test12: List<Long>
        ): Main
    }

    fun scopedInjected(): ScopedInjected
    fun inject(instance: Injected2)
    fun test2(): Long
    fun file(): File
//    fun test3(): List<Long>
}

interface Common {
    fun provider(): String
    fun providerLambda(): () -> String
    fun providerLambdaInt(): () -> Int
    fun injected(): Injected
}

interface Dependency {
    fun file(): File
}

@Module
object TestModule {
    @JvmStatic
    @Provides
    fun string(int: Int): String = int.toString()

    @TestScope
    @JvmStatic
    @Provides
    fun lambdaString(int: Int, string: String): () -> String = { int.toString() + string }.also { println("Invoked lambdaString") }

    @JvmStatic
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
    @set:Inject
    var test1: Int? = null
    @Inject
    lateinit var lambdaInt: () -> Int
    @Inject
    fun setString(string: String, int: Int) {
        println("Have set $string $int")
    }
}

@Module
class TestModuleInstance(val int: Int) {
    @TestScope
    @Provides
    fun integer(): Int = int.also { println("Invoked int") }

//    @Provides
//    fun nullableInteger(): Int? = null
}

@Scope
annotation class TestScope

@Module
abstract class AbstractModule {

    @Module
    companion object {
        @TestScope
        @JvmStatic
        @Provides
        fun test12(): Long = 666L
    }
}

@Module
class ZeroParameterModuleInstance() {
    @Provides
    fun longLambda(value: Long): () -> Long = { value }
}

fun main(args: Array<String>) {
//    val component = DaggerMain.factory().build(TestModuleInstance(3), listOf())
    val component = DaggerMain(
        TestModuleInstance(3),
        object : Dependency {
            override fun file(): File = File("Hello")
        }
    )
    println(component.injected().lambda())
    println(component.injected().lambda())
    println(component.scopedInjected().lambda())
    println(component.scopedInjected().lambda())

    println(component.providerLambda()())

    val injected2 = Injected2()
    component.inject(injected2)
    println(injected2.lambdaString())

    println(component.test2())
    println(component.file())
}
