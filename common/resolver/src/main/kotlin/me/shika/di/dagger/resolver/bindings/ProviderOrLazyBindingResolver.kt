package me.shika.di.dagger.resolver.bindings

import me.shika.di.dagger.resolver.ResolverContext
import me.shika.di.dagger.resolver.classDescriptor
import me.shika.di.model.Binding
import me.shika.di.model.Key
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.descriptors.annotations.AnnotationDescriptor
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe
import org.jetbrains.kotlin.types.KotlinType

class ProviderOrLazyBindingResolver(
    private val type: KotlinType,
    private val source: DeclarationDescriptor,
    private val qualifiers: List<AnnotationDescriptor>,
    private val context: ResolverContext
): BindingResolver {
    override fun invoke(): List<Binding> {
        val classDescriptor = type.unwrap().classDescriptor()
        val innerType by lazy { type.arguments.first().type }

        val bindingType = when (classDescriptor?.fqNameSafe) {
            PROVIDER_FQ_NAME -> Binding.Variation.Provider(source, innerType)
            LAZY_FQ_NAME -> Binding.Variation.Lazy(source, innerType)
            else -> null
        } ?: return emptyList()

        return listOf(
            Binding(
                Key(type, qualifiers),
                emptyList(),
                bindingType
            )
        )
    }
}

private val PROVIDER_FQ_NAME = FqName("javax.inject.Provider")
private val LAZY_FQ_NAME = FqName("dagger.Lazy")
