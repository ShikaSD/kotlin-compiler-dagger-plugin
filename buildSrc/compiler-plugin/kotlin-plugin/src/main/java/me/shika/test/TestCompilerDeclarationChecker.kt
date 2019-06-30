package me.shika.test

import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.descriptors.findClassAcrossModuleDependencies
import org.jetbrains.kotlin.extensions.AnnotationBasedExtension
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtModifierListOwner
import org.jetbrains.kotlin.resolve.DescriptorUtils.isObject
import org.jetbrains.kotlin.resolve.annotations.hasJvmStaticAnnotation
import org.jetbrains.kotlin.resolve.checkers.DeclarationChecker
import org.jetbrains.kotlin.resolve.checkers.DeclarationCheckerContext
import org.jetbrains.kotlin.resolve.constants.KClassValue
import org.jetbrains.kotlin.resolve.scopes.DescriptorKindFilter
import org.jetbrains.kotlin.resolve.scopes.getDescriptorsFiltered
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.util.slicedMap.Slices
import org.jetbrains.kotlin.util.slicedMap.WritableSlice
import java.util.ArrayDeque

internal val DAGGER_COMPONENT: WritableSlice<ClassDescriptor, List<Pair<FunctionDescriptor, List<FunctionDescriptor>>>> =
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
        if (descriptor is ClassDescriptor && descriptor.isDaggerComponent()) {
            reporter.warn("Found dagger component $descriptor")

            val implClass = descriptor.unsubstitutedMemberScope.getDescriptorsFiltered(DescriptorKindFilter.CLASSIFIERS) {
                it.asString() == "Component"
            }.first() as ClassDescriptor
            val endpoints = implClass.findEndpoints()

            reporter.warn("Exposes types $endpoints, impl class $implClass")

            val annotation = descriptor.annotations.findAnnotation(DAGGER_COMPONENT_ANNOTATION)
            val arguments = annotation?.allValueArguments
            val modules = arguments?.get(Name.identifier("modules"))?.value as? List<KClassValue>
            val functions = modules?.flatMap {
                val scope = when (val value = it.value) {
                    is KClassValue.Value.NormalClass -> {
                        context.moduleDescriptor.findClassAcrossModuleDependencies(value.classId)
                            ?.unsubstitutedMemberScope
                    }
                    is KClassValue.Value.LocalClass -> {
                        value.type.unwrap().memberScope
                    }
                }

                scope?.getDescriptorsFiltered(DescriptorKindFilter.FUNCTIONS)
                    ?.filter {
                        it.annotations.hasAnnotation(FqName("dagger.Provides"))
                            && (it.hasJvmStaticAnnotation() || (it.containingDeclaration as? ClassDescriptor)?.let { isObject(it) } == true)
                    }
                    ?: emptyList()
            }
                ?.filterIsInstance<FunctionDescriptor>()
                ?: emptyList()
            reporter.warn("Modules $functions in ${functions?.map { it.containingDeclaration }.distinct()}")

            val result = endpoints.map { endpoint ->
                val providers = endpoint.resolveDependencies(functions, context)
                reporter.warn("$endpoint can be provided with $providers")

                endpoint to providers
            }
            context.trace.record(DAGGER_COMPONENT, implClass, result)
        }
    }

    private fun FunctionDescriptor.resolveDependencies(
        providers: List<FunctionDescriptor>,
        context: DeclarationCheckerContext
    ): List<FunctionDescriptor> {
        val deque = ArrayDeque<KotlinType>()
        var result = mutableListOf<FunctionDescriptor>()
        deque.push(returnType)

        while (deque.isNotEmpty()) {
            val element = deque.pop()
            val canProvide = providers.filter { it ->
                val returnType = it.returnType ?: return@filter false
                returnType.constructor == element.constructor
                    && returnType.arguments == element.arguments
            }

//            if (canProvide.isEmpty()) {
//                context.trace.reportFromPlugin()
//            }

            val nextProvider = canProvide.first()
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

//
//        val injectedTypes = functions.filter { it.valueParameters.isNotEmpty() }
//            .map { it.valueParameters.first().type.constructor.declarationDescriptor as ClassDescriptor }
//            .mapNotNull { it.constructors.firstOrNull { it.annotations.hasAnnotation(FqName("javax.Inject")) } }
//            .flatMap { it.valueParameters.map { it.type } }

        return exposedTypes
    }
}
