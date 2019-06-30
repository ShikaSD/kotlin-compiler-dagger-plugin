package me.shika.test

import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.com.intellij.openapi.project.Project
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.descriptors.Visibilities
import org.jetbrains.kotlin.descriptors.annotations.AnnotationDescriptor
import org.jetbrains.kotlin.descriptors.annotations.Annotations
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.synthetics.SyntheticClassOrObjectDescriptor
import org.jetbrains.kotlin.resolve.extensions.SyntheticResolveExtension
import org.jetbrains.kotlin.resolve.lazy.LazyClassContext
import org.jetbrains.kotlin.resolve.lazy.declarations.ClassMemberDeclarationProvider
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.utils.addIfNotNull

class TestCompilerSyntheticResolveExtension(
    private val project: Project,
    private val reporter: MessageCollector
) : SyntheticResolveExtension {
    override fun getSyntheticNestedClassNames(thisDescriptor: ClassDescriptor): List<Name> =
        if (thisDescriptor.isDaggerComponent()) {
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
        if (thisDescriptor.isDaggerComponent() && name == DAGGER_COMPONENT_IMPL) {
            reporter.warn("$thisDescriptor is dagger component")
            val lexicalScope = ctx.declarationScopeProvider.getResolutionScopeForDeclaration(
                declarationProvider.ownerInfo!!.scopeAnchor
            )

            val classDescriptor = SyntheticClassOrObjectDescriptor(
                ctx,
                declarationProvider.correspondingClassOrObject!!,
                thisDescriptor,
                name,
                thisDescriptor.source,
                lexicalScope,
                Modality.FINAL,
                Visibilities.PUBLIC,
                Annotations.EMPTY,
                Visibilities.PUBLIC,
                ClassKind.CLASS,
                false
            ).apply {
                initialize()
            }

            result += classDescriptor
        }
    }

    override fun addSyntheticSupertypes(thisDescriptor: ClassDescriptor, supertypes: MutableList<KotlinType>) {
        if (thisDescriptor is SyntheticClassOrObjectDescriptor) {
            val declaration = thisDescriptor.containingDeclaration
            if (declaration is ClassDescriptor && declaration.isDaggerComponent()) {
                supertypes.addIfNotNull(declaration.defaultType)
            }
        }
    }
}

val DAGGER_COMPONENT_ANNOTATION = FqName("dagger.Component")
val DAGGER_COMPONENT_IMPL = Name.identifier("Component")

fun ClassDescriptor.isDaggerComponent() =
    annotations.hasAnnotation(DAGGER_COMPONENT_ANNOTATION) && modality == Modality.ABSTRACT && this !is AnnotationDescriptor
