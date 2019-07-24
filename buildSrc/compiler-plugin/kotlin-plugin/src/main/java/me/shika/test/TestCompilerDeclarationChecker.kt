package me.shika.test

import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity.EXCEPTION
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.ConstructorDescriptor
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.descriptors.annotations.AnnotationDescriptor
import org.jetbrains.kotlin.extensions.AnnotationBasedExtension
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtModifierListOwner
import org.jetbrains.kotlin.resolve.checkers.DeclarationChecker
import org.jetbrains.kotlin.resolve.checkers.DeclarationCheckerContext
import org.jetbrains.kotlin.resolve.descriptorUtil.annotationClass
import org.jetbrains.kotlin.resolve.scopes.DescriptorKindFilter
import org.jetbrains.kotlin.resolve.scopes.getDescriptorsFiltered
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.util.slicedMap.Slices
import org.jetbrains.kotlin.util.slicedMap.WritableSlice
import java.util.*

internal val DAGGER_RESOLUTION_RESULT: WritableSlice<ClassDescriptor, List<Pair<FunctionDescriptor, List<FunctionDescriptor>>>> =
    Slices.createSimpleSlice()
internal val DAGGER_SCOPED_BINDINGS: WritableSlice<ClassDescriptor, List<FunctionDescriptor>> =
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
            val implClass = descriptor.unsubstitutedMemberScope.getDescriptorsFiltered(DescriptorKindFilter.CLASSIFIERS) {
                it.asString() == "Component"
            }.first() as ClassDescriptor
            val endpoints = descriptor.findEndpoints()

            reporter.warn("Exposes types $endpoints, impl class $implClass")

            val modules = context.trace.get(DAGGER_MODULES, implClass)
            val functions = modules
                ?.filterIsInstance<ClassDescriptor>()
                ?.flatMap {
                    it.unsubstitutedMemberScope.getDescriptorsFiltered(DescriptorKindFilter.FUNCTIONS)
                        .filter {
                            it.annotations.hasAnnotation(FqName("dagger.Provides"))
                        }
                }
                ?.filterIsInstance<FunctionDescriptor>()
                ?: emptyList()
            reporter.warn("Modules $functions in ${functions.map { it.containingDeclaration }.distinct()}")

            val result = endpoints.map { endpoint ->
                val providers = endpoint.resolveDependencies(functions, descriptor, implClass, context)
                reporter.warn("$endpoint can be provided with $providers")

                endpoint to providers
            }

            context.trace.record(DAGGER_RESOLUTION_RESULT, implClass, result)
        }
    }

    private fun FunctionDescriptor.resolveDependencies(
        providers: List<FunctionDescriptor>,
        componentDescriptor: ClassDescriptor,
        implDescriptor: ClassDescriptor,
        context: DeclarationCheckerContext
    ): List<FunctionDescriptor> {
        val componentScopes = componentDescriptor.scopeAnnotations()

        val deque = ArrayDeque<KotlinType>()
        val result = mutableListOf<FunctionDescriptor>()
        val scopedProviders = mutableListOf<FunctionDescriptor>()
        deque.push(returnType)

        while (deque.isNotEmpty()) {
            val element = deque.pop()
            val canProvide = providers.filter {
                val returnType = it.returnType ?: return@filter false
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

            val nextProvider = canProvide.first()

            val scopeAnnotations = nextProvider.scopeAnnotations().map { it.fqName }
            val componentScopeNames = componentScopes.map { it.fqName }
            if (!componentScopeNames.containsAll(scopeAnnotations)) {
                reporter.report(
                    EXCEPTION,
                    "Component $componentDescriptor and ${nextProvider.name} scopes do not match: " +
                            "component - $componentScopeNames," +
                            " binding - $scopeAnnotations"
                )
            }
            if (!scopeAnnotations.isEmpty()) {
                scopedProviders.add(nextProvider)
            }

            nextProvider.valueParameters.forEach {
                deque.push(it.type)
            }

            result.add(nextProvider)
        }

        return result
    }

    private fun ClassDescriptor.findEndpoints(): List<FunctionDescriptor> {
        val functions = unsubstitutedMemberScope.getDescriptorsFiltered(DescriptorKindFilter.FUNCTIONS) {
            it.asString() !in setOf("equals", "hashCode", "toString")
        }.filterIsInstance<FunctionDescriptor>()

        val exposedTypes = functions.filter { it.valueParameters.isEmpty() }

//        val injectedTypes = functions.filter { it.valueParameters.isNotEmpty() }
//            .map { it.valueParameters.first().type.constructor.declarationDescriptor as ClassDescriptor }
//            .flatMap { it.valueParameters.map { it.type } }

        return exposedTypes
    }

    private fun KotlinType.injectableConstructor(): ConstructorDescriptor? {
        val classDescriptor = constructor.declarationDescriptor as? ClassDescriptor ?: return null
        val injectableConstructors = classDescriptor.constructors.filter {
            it.annotations.hasAnnotation(FqName("javax.inject.Inject"))
        }
        if (injectableConstructors.size > 1) {
            reporter.report(EXCEPTION, "Class can have only one @Inject annotated constructor, found $injectableConstructors")
        }
        return injectableConstructors.firstOrNull()
    }
}

fun DeclarationDescriptor.scopeAnnotations(): List<AnnotationDescriptor> =
    annotations.filter {
        it.annotationClass?.annotations?.hasAnnotation(FqName("javax.inject.Scope")) == true
    }
