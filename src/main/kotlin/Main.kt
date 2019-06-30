import dagger.Component
import dagger.Module
import dagger.Provides
import javax.Inject

@Component(modules = [TestModule::class])
interface Main {
    fun provider(): String
    fun providerLambda(): () -> String
}

@Module
object TestModule {

    @Provides
    fun int(): Int = 0

    @Provides
    fun string(int: Int): String = int.toString()

    @Provides
    fun lambdaString(int: Int, string: String): () -> String = { int.toString() + string }

    @Provides
    fun lambdaInt(int: Int): () -> Int = { int }
}

fun test(): () -> String {
    val int = TestModule.int()
    val string = TestModule.string(int)
    val lambdaString = TestModule.lambdaString(int, string)
    return lambdaString
}

fun main() {
    println(test())

    val component = Main.Component()
    println(component.providerLambda()())
}

