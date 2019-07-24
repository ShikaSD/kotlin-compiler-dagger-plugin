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
}

@Module
object TestModule {
    @Provides
    fun string(int: Int): String = int.toString()

    @TestScope
    @Provides
    fun lambdaString(int: Int, string: String): () -> String = { int.toString() + string }

    @Provides
    fun lambdaInt(int: Int): () -> Int = { int }
}

class Injected @Inject constructor(val lambda: () -> String)

@Module
class TestModuleInstance(val int: Int) {
    @Provides
    fun int(): Int = int
}

@Scope
annotation class TestScope

fun main(args: Array<String>) {
    val component = Main.Component(TestModuleInstance(3))
    println(component.injected().lambda())
}
