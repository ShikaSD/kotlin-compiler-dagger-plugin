package me.shika.test.dagger.resolver.creator

import me.shika.test.FACTORY_DEPENDENCIES_NOT_DECLARED
import me.shika.test.FACTORY_DEPENDENCIES_NOT_PROVIDED
import me.shika.test.FACTORY_MODULE_NOT_PROVIDED
import me.shika.test.FACTORY_WRONG_METHOD
import me.shika.test.dagger.resolver.ComponentAnnotationDescriptor
import me.shika.test.dagger.resolver.ResolverContext
import me.shika.test.dagger.resolver.report
import me.shika.test.model.Binding
import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.descriptors.ValueParameterDescriptor
import org.jetbrains.kotlin.diagnostics.DiagnosticFactory1
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.resolve.scopes.DescriptorKindFilter
import org.jetbrains.kotlin.resolve.scopes.getDescriptorsFiltered
import org.jetbrains.kotlin.types.KotlinType

class DaggerFactoryDescriptor(
    private val component: ClassDescriptor,
    private val definition: ClassDescriptor,
    private val componentAnnotation: ComponentAnnotationDescriptor,
    private val context: ResolverContext
) : CreatorDescriptor {
    var method: FunctionDescriptor? = null
        private set

    override var instances: List<Binding> = emptyList()
        private set

    private val declaredDependencies =
        componentAnnotation.dependencies
    private val declaredModuleInstances =
        componentAnnotation.moduleInstances

    init {
        parseDefinition()
    }

    private fun parseDefinition() {
        val factoryMethods = definition.methods().filter { it.returnType == component.defaultType }
        if (factoryMethods.size != 1) {
            definition.report(context.trace) { FACTORY_WRONG_METHOD.on(it, factoryMethods) }
            return
        }

        method = factoryMethods.first()

        val instanceParams = method!!.valueParameters.filter { it.annotations.hasAnnotation(DAGGER_BINDS_INSTANCE) }
        val dependencyParams = method!!.valueParameters.asSequence().filterNot { it in instanceParams }

        val notProvidedDependencies = declaredDependencies.filterNot { it.defaultType in dependencyParams.map { it.type } }
        reportOnMethod(notProvidedDependencies.map { it.defaultType }, FACTORY_DEPENDENCIES_NOT_PROVIDED)

        val notProvidedModules = declaredModuleInstances.filterNot { it.defaultType in dependencyParams.map { it.type } }
        reportOnMethod(notProvidedModules.map { it.defaultType }, FACTORY_MODULE_NOT_PROVIDED)

        val notDeclared = dependencyParams.filterNot { param ->
            declaredDependencies.any { param.type == it.defaultType }
                || declaredModuleInstances.any { param.type == it.defaultType }
        }
        reportNotDeclared(notDeclared.toList())

        instances = instanceParams.map { it.toInstanceBinding() }
    }

    private fun reportNotDeclared(list: List<ValueParameterDescriptor>) {
        list.forEach {
            it.report(context.trace) { FACTORY_DEPENDENCIES_NOT_DECLARED.on(it) }
        }
    }

    private fun reportOnMethod(list: List<KotlinType>, diagnosticFactory: DiagnosticFactory1<PsiElement, KotlinType>) {
        list.forEach { type ->
            method?.report(context.trace) { diagnosticFactory.on(it, type) }
        }
    }
}

private fun ClassDescriptor.methods() =
    unsubstitutedMemberScope.getDescriptorsFiltered(DescriptorKindFilter.FUNCTIONS) { true }
        .filterIsInstance<FunctionDescriptor>()

private val DAGGER_BINDS_INSTANCE = FqName("dagger.BindsInstance")
