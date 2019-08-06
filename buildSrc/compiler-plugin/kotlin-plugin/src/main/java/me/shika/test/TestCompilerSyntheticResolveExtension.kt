package me.shika.test

import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.com.intellij.openapi.project.Project
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.ClassifierDescriptor
import org.jetbrains.kotlin.descriptors.Modality.ABSTRACT
import org.jetbrains.kotlin.descriptors.Modality.FINAL
import org.jetbrains.kotlin.descriptors.Visibilities
import org.jetbrains.kotlin.descriptors.annotations.AnnotationDescriptor
import org.jetbrains.kotlin.descriptors.annotations.Annotations
import org.jetbrains.kotlin.descriptors.impl.ClassConstructorDescriptorImpl
import org.jetbrains.kotlin.descriptors.impl.ValueParameterDescriptorImpl
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.synthetics.SyntheticClassOrObjectDescriptor
import org.jetbrains.kotlin.resolve.DescriptorUtils.isObject
import org.jetbrains.kotlin.resolve.constants.KClassValue
import org.jetbrains.kotlin.resolve.extensions.SyntheticResolveExtension
import org.jetbrains.kotlin.resolve.lazy.LazyClassContext
import org.jetbrains.kotlin.resolve.lazy.declarations.ClassMemberDeclarationProvider
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.util.slicedMap.Slices
import org.jetbrains.kotlin.util.slicedMap.WritableSlice
import org.jetbrains.kotlin.utils.addIfNotNull

val DAGGER_MODULES: WritableSlice<ClassDescriptor, List<ClassifierDescriptor>> = Slices.createSimpleSlice()
val DAGGER_MODULE_INSTANCES: WritableSlice<ClassDescriptor, List<ClassifierDescriptor>> = Slices.createSimpleSlice()

class TestCompilerSyntheticResolveExtension(
    private val project: Project,
    private val reporter: MessageCollector
) : SyntheticResolveExtension {
    override fun getSyntheticNestedClassNames(thisDescriptor: ClassDescriptor): List<Name> =
        if (thisDescriptor.componentAnnotation() != null) {
            listOf(DAGGER_COMPONENT_IMPL)
        } else {
            emptyList()
        }


    override fun generateSyntheticClasses(
        thisDescriptor: ClassDescriptor,
        name: Name,
        ctx: LazyClassContext,
        declarationProvider: ClassMemberDeclarationProvider,
        result: MutableSet<ClassDescriptor>
    ) {
        val componentAnnotation = thisDescriptor.componentAnnotation()
        if (componentAnnotation != null && name == DAGGER_COMPONENT_IMPL) {
            reporter.warn("$thisDescriptor is dagger component")
            val lexicalScope = ctx.declarationScopeProvider.getResolutionScopeForDeclaration(
                declarationProvider.ownerInfo!!.scopeAnchor
            )

            val modules = componentAnnotation.componentModules()
                .mapNotNull {
                    val value = it.getArgumentType(ctx.moduleDescriptor)
                    value.constructor.declarationDescriptor as? ClassDescriptor
                }
            val moduleInstances = modules.filter { !isObject(it) && it.modality != ABSTRACT }
            reporter.warn("Has $moduleInstances module instances")

            val classDescriptor = SyntheticClassOrObjectDescriptor(
                c = ctx,
                parentClassOrObject = declarationProvider.correspondingClassOrObject!!,
                containingDeclaration = thisDescriptor,
                name = name,
                source = thisDescriptor.source,
                outerScope = lexicalScope,
                modality = FINAL,
                visibility = Visibilities.PUBLIC,
                annotations = Annotations.EMPTY,
                constructorVisibility = Visibilities.PUBLIC,
                kind = ClassKind.CLASS,
                isCompanionObject = false
            ).apply {
                initialize()

                secondaryConstructors = listOf(
                    ClassConstructorDescriptorImpl.create(
                        this,
                        Annotations.EMPTY,
                        false,
                        source
                    ).apply {
                        initialize(
                            moduleInstances.mapIndexed { index, it ->
                                ValueParameterDescriptorImpl(
                                    containingDeclaration = this,
                                    original = null,
                                    index = index,
                                    annotations = Annotations.EMPTY,
                                    name = Name.identifier(it.name.asString().decapitalize()),
                                    outType = it.defaultType,
                                    declaresDefaultValue = false,
                                    isCrossinline = false,
                                    isNoinline = false,
                                    varargElementType = null,
                                    source = source
                                )
                            },
                            Visibilities.PUBLIC,
                            emptyList()
                        )
                        returnType = containingDeclaration.defaultType
                    }
                )
            }

            result += classDescriptor
            ctx.trace.record(DAGGER_MODULES, classDescriptor, modules)
            ctx.trace.record(DAGGER_MODULE_INSTANCES, classDescriptor, moduleInstances)
        }
    }

    override fun addSyntheticSupertypes(thisDescriptor: ClassDescriptor, supertypes: MutableList<KotlinType>) {
        if (thisDescriptor is SyntheticClassOrObjectDescriptor) {
            val declaration = thisDescriptor.containingDeclaration
            if (declaration is ClassDescriptor && declaration.componentAnnotation() != null) {
                supertypes.addIfNotNull(declaration.defaultType)
            }
        }
    }
}

val DAGGER_COMPONENT_ANNOTATION = FqName("dagger.Component")
val DAGGER_COMPONENT_IMPL = Name.identifier("DaggerComponent")

fun ClassDescriptor.componentAnnotation() =
    if (modality == ABSTRACT && this !is AnnotationDescriptor) {
        annotations.findAnnotation(DAGGER_COMPONENT_ANNOTATION)
    } else {
        null
    }

fun AnnotationDescriptor.componentModules() =
    allValueArguments[Name.identifier("modules")]
        ?.value as? List<KClassValue>
        ?: emptyList()
