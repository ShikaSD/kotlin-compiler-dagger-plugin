package me.shika.test

import me.shika.test.model.Binding
import me.shika.test.model.Endpoint
import me.shika.test.model.Injectable
import me.shika.test.model.ResolveResult
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity.EXCEPTION
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.descriptors.PropertyDescriptor
import org.jetbrains.kotlin.descriptors.annotations.AnnotationDescriptor
import org.jetbrains.kotlin.extensions.AnnotationBasedExtension
import org.jetbrains.kotlin.ir.expressions.typeParametersCount
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtModifierListOwner
import org.jetbrains.kotlin.resolve.BindingTrace
import org.jetbrains.kotlin.resolve.checkers.DeclarationChecker
import org.jetbrains.kotlin.resolve.checkers.DeclarationCheckerContext
import org.jetbrains.kotlin.resolve.descriptorUtil.annotationClass
import org.jetbrains.kotlin.resolve.scopes.DescriptorKindFilter
import org.jetbrains.kotlin.resolve.scopes.getDescriptorsFiltered
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.util.slicedMap.Slices
import org.jetbrains.kotlin.util.slicedMap.WritableSlice
import java.util.*

internal val DAGGER_RESOLUTION_RESULT: WritableSlice<ClassDescriptor, List<ResolveResult>> =
    Slices.createSimpleSlice()
internal val DAGGER_SCOPED_BINDINGS: WritableSlice<ClassDescriptor, MutableSet<Binding>> =
    Slices.createSimpleSlice()

class TestCompilerDeclarationChecker(
    private val reporter: MessageCollector
): DeclarationChecker, AnnotationBasedExtension {
    override fun getAnnotationFqNames(modifierListOwner: KtModifierListOwner?): List<String> =
        listOf("dagger.Component")

    override fun check(
        declaration: KtDeclaration,
        descriptor: DeclarationDescriptor,
        context: DeclarationCheckerContext
    ) {
        if (descriptor is ClassDescriptor && descriptor.componentAnnotation() != null) {
            reporter.warn("Found dagger component $descriptor")
            val implClass = descriptor.unsubstitutedMemberScope
                .getDescriptorsFiltered(DescriptorKindFilter.CLASSIFIERS) {
                    it == DAGGER_COMPONENT_IMPL // haxx
                }.first() as ClassDescriptor

            val endpoints = descriptor.findEndpoints(context.trace)
            val bindings = implClass.findBindings(context.trace)

            val result = endpoints.map { endpoint ->
                val result = endpoint.resolveDependencies(bindings, descriptor, implClass, context.trace)
                reporter.warn("$endpoint can be provided with $result")
                result
            }

            context.trace.record(DAGGER_RESOLUTION_RESULT, implClass, result)
        }
    }

    private fun Endpoint.resolveDependencies(
        bindings: List<Binding>,
        componentDescriptor: ClassDescriptor,
        implDescriptor: ClassDescriptor,
        trace: BindingTrace
    ): ResolveResult {
        val componentScopes = componentDescriptor.scopeAnnotations()

        val deque = ArrayDeque<KotlinType>()
        val result = mutableSetOf<Binding>()
        val scopedBindings = mutableSetOf<Binding>()

        deque.push(type!!)

        while (deque.isNotEmpty()) {
            val element = deque.pop()
            val canProvide = bindings.filter {
                val returnType = it.type ?: return@filter false
                returnType.constructor == element.constructor && returnType.arguments == element.arguments
            } + listOfNotNull(element.injectableConstructor())

            if (canProvide.isEmpty()) {
//                context.trace.reportFromPlugin()
                reporter.report(
                    EXCEPTION,
                    "Cannot provide $element"
                )
            }

            if (canProvide.size > 1) {
                reporter.report(
                    EXCEPTION,
                    "Ambiguous binding for $element: found $canProvide"
                )
            }

            val binding = canProvide.first()
            val bindingDescriptor = binding.resolvedDescriptor

            val scopeAnnotations = when (binding) {
                is Binding.InstanceFunction,
                is Binding.StaticFunction -> bindingDescriptor.scopeAnnotations()
                is Binding.Constructor -> (bindingDescriptor.containingDeclaration as ClassDescriptor).scopeAnnotations()
            }.map { it.fqName }

            val componentScopeNames = componentScopes.map { it.fqName }
            if (!componentScopeNames.containsAll(scopeAnnotations)) {
                reporter.report(
                    EXCEPTION,
                    "Component $componentDescriptor and ${bindingDescriptor.name} scopes do not match: " +
                            "component - $componentScopeNames," +
                            " binding - $scopeAnnotations"
                )
            }
            if (!scopeAnnotations.isEmpty()) {
                scopedBindings.add(binding)
            }

            bindingDescriptor.valueParameters.forEach {
                deque.push(it.type)
            }

            result.add(binding)
        }

        val recordedScopedProviders = trace.get(DAGGER_SCOPED_BINDINGS, implDescriptor)
            ?: mutableSetOf<Binding>().also { trace.record(DAGGER_SCOPED_BINDINGS, implDescriptor, it) }
        recordedScopedProviders.addAll(scopedBindings)

        return ResolveResult(this, result)
    }

    private fun ClassDescriptor.findEndpoints(trace: BindingTrace): List<Endpoint> {
        val functions = unsubstitutedMemberScope.getDescriptorsFiltered(DescriptorKindFilter.FUNCTIONS) {
            it.asString() !in setOf("equals", "hashCode", "toString") // haxx
        }.asSequence()
            .filterIsInstance<FunctionDescriptor>()

        val exposedTypes = functions
            .filter { it.valueParameters.isEmpty() }
            .map { Endpoint.Exposed(it) }

        val injectables = functions
            .filter { it.valueParameters.isNotEmpty() }
            .flatMap { func ->
                val cls = func.valueParameters.first().type.constructor.declarationDescriptor as ClassDescriptor
                val fields =
                    cls.unsubstitutedMemberScope.getDescriptorsFiltered(DescriptorKindFilter.VARIABLES) { true }
                        .asSequence()
                        .filterIsInstance<PropertyDescriptor>()
                        .filter { it.backingField?.annotations?.hasAnnotation(FqName("javax.inject.Inject")) == true }
                        .map {
                            Endpoint.Injected(func, Injectable.Property(cls, it))
                        }

                val setters = cls.unsubstitutedMemberScope.getDescriptorsFiltered(DescriptorKindFilter.FUNCTIONS) { true }
                    .asSequence()
                    .filterIsInstance<FunctionDescriptor>()
                    .filter { cls.annotations.hasAnnotation(FqName("javax.inject.Inject")) }
                    .map {
                        Endpoint.Injected(func, Injectable.Setter(cls, it))
                    }

                fields + setters
            }

        return (exposedTypes + injectables).toList()
    }

    private fun KotlinType.injectableConstructor(): Binding.Constructor? {
        val classDescriptor = constructor.declarationDescriptor as? ClassDescriptor ?: return null
        val injectableConstructors = classDescriptor.constructors.filter {
            it.annotations.hasAnnotation(FqName("javax.inject.Inject")) && it.typeParametersCount == 0
        }
        if (injectableConstructors.size > 1) {
            reporter.report(EXCEPTION, "Class can have only one @Inject annotated constructor, found $injectableConstructors")
        }
        return injectableConstructors.firstOrNull()?.let { Binding.Constructor(it) }
    }

    private fun ClassDescriptor.findBindings(trace: BindingTrace): List<Binding> {
        val modules = trace.get(DAGGER_MODULES, this)
        val instances = trace.get(DAGGER_MODULE_INSTANCES, this)!!
        return modules
            ?.filterIsInstance<ClassDescriptor>()
            ?.flatMap { module ->
                val companionBindings = module.companionObjectDescriptor
                    ?.unsubstitutedMemberScope
                    ?.getDescriptorsFiltered(DescriptorKindFilter.FUNCTIONS)
                    ?: emptyList()

                (module.unsubstitutedMemberScope.getDescriptorsFiltered(DescriptorKindFilter.FUNCTIONS) + companionBindings)
                    .filter { it.annotations.hasAnnotation(FqName("dagger.Provides")) }
                    .filterIsInstance<FunctionDescriptor>()
                    .map {
                        when (module) {
                            in instances -> Binding.InstanceFunction(module, it)
                            else -> Binding.StaticFunction(it)
                        }
                    }
            }
            ?: emptyList()
    }
}

fun DeclarationDescriptor.scopeAnnotations(): List<AnnotationDescriptor> =
    annotations.filter {
        it.annotationClass?.annotations?.hasAnnotation(FqName("javax.inject.Scope")) == true
    }


