package me.shika.di.dagger.resolver.bindings

import me.shika.di.MORE_THAN_ONE_INJECT_CONSTRUCTOR
import me.shika.di.dagger.resolver.INJECT_FQ_NAME
import me.shika.di.dagger.resolver.ResolverContext
import me.shika.di.dagger.resolver.classDescriptor
import me.shika.di.dagger.resolver.qualifiers
import me.shika.di.dagger.resolver.report
import me.shika.di.dagger.resolver.scopeAnnotations
import me.shika.di.model.Binding
import me.shika.di.model.Key
import org.jetbrains.kotlin.types.KotlinType

class InjectConstructorBindingResolver(
    private val type: KotlinType,
    private val context: ResolverContext
): BindingResolver {
    override fun invoke(): List<Binding> {
        val classDescriptor = type.classDescriptor() ?: return emptyList()
        val injectableConstructors = classDescriptor.constructors.filter { it.annotations.hasAnnotation(INJECT_FQ_NAME) }
        if (injectableConstructors.size > 1) {
            classDescriptor.report(context.trace) {
                MORE_THAN_ONE_INJECT_CONSTRUCTOR.on(it, classDescriptor)
            }
        }
        return listOfNotNull(
            injectableConstructors.firstOrNull()?.let {
                Binding(
                    Key(type, it.qualifiers().ifEmpty { classDescriptor.qualifiers() }),
                    classDescriptor.scopeAnnotations(),
                    Binding.Variation.Constructor(it)
                )
            }
        )
    }

}
