import dagger.Component

@TestScope
@Component(modules = [TestModuleInstance::class], dependencies = [some.SomeDependency::class, other.SomeDependency::class])
interface DefaultBuilderСomponent {
    fun integer(): Int
    fun double(): Double
}
