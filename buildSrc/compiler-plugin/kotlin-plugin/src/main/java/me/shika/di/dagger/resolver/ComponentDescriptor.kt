package me.shika.di.dagger.resolver

import me.shika.di.COMPONENT_NOT_ABSTRACT
import me.shika.di.COMPONENT_TYPE_PARAMETER
import me.shika.di.COMPONENT_WITH_MULTIPLE_FACTORIES
import me.shika.di.dagger.resolver.bindings.CreatorInstanceBindingResolver
import me.shika.di.dagger.resolver.bindings.DependencyBindingResolver
import me.shika.di.dagger.resolver.bindings.ModuleBindingResolver
import me.shika.di.dagger.resolver.creator.CreatorDescriptor
import me.shika.di.dagger.resolver.creator.DaggerFactoryDescriptor
import me.shika.di.dagger.resolver.endpoints.InjectionEndpointResolver
import me.shika.di.dagger.resolver.endpoints.ProvisionEndpointResolver
import me.shika.di.model.Binding
import me.shika.di.model.Key
import me.shika.di.model.ResolveResult
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.descriptors.annotations.AnnotationDescriptor
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.resolve.scopes.DescriptorKindFilter
import org.jetbrains.kotlin.resolve.scopes.getDescriptorsFiltered

class DaggerComponentDescriptor(
    val definition: ClassDescriptor,
    val file: KtFile,
    val context: ResolverContext
) {
    init {
        parseDefinition()
    }

    var creatorDescriptor: CreatorDescriptor? = null
        private set

    lateinit var graph: List<ResolveResult>
        private set

    private fun parseDefinition() {
        val componentAnnotation = definition.componentAnnotation()?.let {
            ComponentAnnotationDescriptor(context, it)
        } ?: return
        val scopes = definition.scopeAnnotations()

        creatorDescriptor = definition.findFactory()?.let { creatorClass ->
            DaggerFactoryDescriptor(definition, creatorClass, componentAnnotation, context)
        } // TODO: Builder

        val bindingResolvers = listOfNotNull(
            ModuleBindingResolver(componentAnnotation, definition, context),
            DependencyBindingResolver(componentAnnotation, definition, context),
            creatorDescriptor?.let { CreatorInstanceBindingResolver(it) },
            { listOf(componentBinding()) }
        )

        val endpointResolvers = listOf(
            ProvisionEndpointResolver(componentAnnotation, definition, context),
            InjectionEndpointResolver(componentAnnotation, definition, context)
        )

        graph = GraphBuilder(
            context,
            scopes,
            endpointResolvers.flatMap { it() },
            bindingResolvers.flatMap { it() }
        ).build()
    }

    private fun ClassDescriptor.componentAnnotation() =
        when {
            modality != Modality.ABSTRACT || this is AnnotationDescriptor -> {
                report(context.trace) { COMPONENT_NOT_ABSTRACT.on(it) }
                null
            }
            declaredTypeParameters.isNotEmpty() -> {
                report(context.trace) { COMPONENT_TYPE_PARAMETER.on(it) }
                null
            }
            else -> annotations.findAnnotation(DAGGER_COMPONENT_ANNOTATION)
        }

    private fun ClassDescriptor.findFactory(): ClassDescriptor? {
        val factories = innerClasses()
            .filter { it.annotations.hasAnnotation(DAGGER_FACTORY_ANNOTATION) }

        if (factories.size > 1) {
            report(context.trace) { COMPONENT_WITH_MULTIPLE_FACTORIES.on(it, factories) }
            return null
        }

        return factories.firstOrNull()
    }

    private fun componentBinding() = Binding(
        Key(definition.defaultType, emptyList()),
        emptyList(),
        Binding.Variation.Component(definition)
    )
}

private val DAGGER_COMPONENT_ANNOTATION = FqName("dagger.Component")
private val DAGGER_FACTORY_ANNOTATION = FqName("dagger.Component.Factory")

private fun ClassDescriptor.innerClasses() =
    unsubstitutedMemberScope.getDescriptorsFiltered(DescriptorKindFilter.CLASSIFIERS) { true }
        .filterIsInstance<ClassDescriptor>()
        .filter { it.kind == ClassKind.INTERFACE || (it.kind == ClassKind.CLASS && it.modality == Modality.ABSTRACT) }

fun ClassDescriptor.isComponent() =
    annotations.hasAnnotation(DAGGER_COMPONENT_ANNOTATION)
