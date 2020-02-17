package me.shika.di

import me.shika.di.DiCommandLineProcessor.Companion.KEY_ENABLED
import me.shika.di.DiCommandLineProcessor.Companion.KEY_SOURCES
import me.shika.di.dagger.resolver.classDescriptor
import org.gradle.api.logging.Logging
import org.jetbrains.kotlin.com.intellij.mock.MockProject
import org.jetbrains.kotlin.compiler.plugin.ComponentRegistrar
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.descriptors.CallableDescriptor
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.resolve.BindingTrace
import org.jetbrains.kotlin.resolve.calls.model.ResolvedCall
import org.jetbrains.kotlin.resolve.jvm.extensions.AnalysisHandlerExtension
import org.jetbrains.kotlin.resolve.scopes.DescriptorKindExclude
import org.jetbrains.kotlin.resolve.scopes.DescriptorKindFilter.Companion.CALLABLES
import org.jetbrains.kotlin.resolve.scopes.LexicalScope
import org.jetbrains.kotlin.resolve.scopes.utils.collectDescriptorsFiltered
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.typeUtil.isAnyOrNullableAny
import org.jetbrains.kotlin.types.typeUtil.isSubtypeOf
import org.jetbrains.kotlin.util.slicedMap.Slices

//@AutoService(ComponentRegistrar::class)
class DiCompilerComponentRegistrar: ComponentRegistrar {
    override fun registerProjectComponents(project: MockProject, configuration: CompilerConfiguration) {
        if (configuration[KEY_ENABLED] != true) {
            return
        }
        val sourcesDir = configuration[KEY_SOURCES] ?: return
//
//        CompilerConfigurationExtension.registerExtension(
//            project,
//            object : CompilerConfigurationExtension {
//                override fun updateConfiguration(configuration: CompilerConfiguration) {
//                    configuration.put(JVMConfigurationKeys.IR, true)
//                }
//
//            }
//        )

        AnalysisHandlerExtension.registerExtension(
            project,
            DiCompilerAnalysisExtension(sourcesDir = sourcesDir)
        )

//        StorageComponentContainerContributor.registerExtension(
//            project,
//            object : StorageComponentContainerContributor {
//                override fun registerModuleComponents(
//                    container: StorageComponentContainer,
//                    platform: TargetPlatform,
//                    moduleDescriptor: ModuleDescriptor
//                ) {
//                    container.registerInstance(object : CallChecker {
//                        override fun check(
//                            resolvedCall: ResolvedCall<*>,
//                            reportOn: PsiElement,
//                            context: CallCheckerContext
//                        ) {
//                            resolvedCall.apply {
//                                if (!resultingDescriptor.annotations.hasAnnotation(FqName("dsl.dsl"))) return@apply
//
//                                val extension = resultingDescriptor.valueParameters.findLast { it.type.isExtensionFunctionType }
//                                val dslBlockType = extension?.type?.getReceiverTypeFromFunctionType()!!
//                                context.trace.record(
//                                    REQUIRED_EXTENSIONS,
//                                    this,
//                                    dslBlockType.getExtensions(context.scope) + dslBlockType.getCallables()
//                                )
//                            }
//                        }
//                    })
//                }
//            }
//        )

    }

}

private fun KotlinType.getExtensions(scope: LexicalScope): List<DeclarationDescriptor> {
    val extensionKindFilter = CALLABLES exclude DescriptorKindExclude.NonExtensions
    return scope.collectDescriptorsFiltered(extensionKindFilter, { true })
        .filterIsInstance<CallableDescriptor>()
        .filter {
            val receiver = it.extensionReceiverParameter
            val receiverType = receiver?.value?.type
            it.annotations.hasAnnotation(REQUIRED_FQ_NAME) && receiverType != null && this.isSubtypeOf(receiverType) && !receiverType.isAnyOrNullableAny()
        }
}

private fun KotlinType.getCallables(): List<DeclarationDescriptor> {
    val classifier = classDescriptor()
    return classifier?.unsubstitutedMemberScope?.getContributedDescriptors(CALLABLES) { true }
        ?.filter {
            val receiverType = (it as? CallableDescriptor)?.dispatchReceiverParameter?.value?.type
            it.annotations.hasAnnotation(REQUIRED_FQ_NAME) && receiverType != null && this.isSubtypeOf(receiverType) && !receiverType.isAnyOrNullableAny()
        } ?: emptyList()
}

fun ResolvedCall<*>.requiredExtensions(bindingTrace: BindingTrace): List<DeclarationDescriptor> =
    bindingTrace[REQUIRED_EXTENSIONS, this] ?: emptyList()

private val REQUIRED_EXTENSIONS = Slices.createSimpleSlice<ResolvedCall<*>, List<DeclarationDescriptor>>()
private val REQUIRED_FQ_NAME = FqName("dsl.required")
