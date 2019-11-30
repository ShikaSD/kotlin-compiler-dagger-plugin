import dagger.Component
import dagger.Lazy
import javax.inject.Provider

@TestScope
@Component(modules = [TestModuleInstance::class])
interface BuilderMain {
    @Component.Builder
    interface Builder {
        fun module(module: TestModuleInstance): Builder
        fun build(): BuilderMain
    }

    fun integer(): Int
    fun integerProvider(): Provider<Int>
    fun integerLazy(): Lazy<Int>
}
