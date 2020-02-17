@file:Suppress("HasPlatformType")

package me.shika.di

import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.descriptors.annotations.AnnotationDescriptor
import org.jetbrains.kotlin.diagnostics.DiagnosticFactory0
import org.jetbrains.kotlin.diagnostics.DiagnosticFactory1
import org.jetbrains.kotlin.diagnostics.DiagnosticFactory2
import org.jetbrains.kotlin.diagnostics.Severity
import org.jetbrains.kotlin.diagnostics.rendering.DefaultErrorMessages
import org.jetbrains.kotlin.diagnostics.rendering.DiagnosticFactoryToRendererMap
import org.jetbrains.kotlin.diagnostics.rendering.DiagnosticParameterRenderer
import org.jetbrains.kotlin.diagnostics.rendering.Renderers.RENDER_TYPE
import org.jetbrains.kotlin.diagnostics.rendering.RenderingContext
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe
import org.jetbrains.kotlin.types.KotlinType

val COMPONENT_NOT_ABSTRACT = DiagnosticFactory0.create<PsiElement>(Severity.ERROR)
val COMPONENT_TYPE_PARAMETER = DiagnosticFactory0.create<PsiElement>(Severity.ERROR)
val COMPONENT_WITH_MULTIPLE_FACTORIES = DiagnosticFactory1.create<PsiElement, List<ClassDescriptor>>(Severity.ERROR)
val COMPONENT_WITH_MULTIPLE_BUILDERS = DiagnosticFactory1.create<PsiElement, List<ClassDescriptor>>(Severity.ERROR)
val COMPONENT_WITH_FACTORY_AND_BUILDER = DiagnosticFactory2.create<PsiElement, List<ClassDescriptor>, List<ClassDescriptor>>(Severity.ERROR)

val MODULE_WITHOUT_ANNOTATION = DiagnosticFactory1.create<PsiElement, ClassDescriptor>(Severity.ERROR)

val FACTORY_WRONG_METHOD = DiagnosticFactory1.create<PsiElement, List<FunctionDescriptor>>(Severity.ERROR)
val FACTORY_DEPENDENCIES_NOT_DECLARED = DiagnosticFactory0.create<PsiElement>(Severity.ERROR)
val FACTORY_DEPENDENCIES_NOT_PROVIDED = DiagnosticFactory1.create<PsiElement, KotlinType>(Severity.ERROR)
val FACTORY_MODULE_NOT_PROVIDED = DiagnosticFactory1.create<PsiElement, KotlinType>(Severity.ERROR)

val BUILDER_WRONG_BUILD_METHOD = DiagnosticFactory1.create<PsiElement, List<DeclarationDescriptor>>(Severity.ERROR)
val BUILDER_SETTER_TOO_MANY_PARAMS = DiagnosticFactory0.create<PsiElement>(Severity.ERROR)
val BUILDER_SETTER_WRONG_RETURN_TYPE = DiagnosticFactory0.create<PsiElement>(Severity.ERROR)
val BUILDER_DEPENDENCIES_NOT_PROVIDED = DiagnosticFactory1.create<PsiElement, KotlinType>(Severity.ERROR)
val BUILDER_MODULE_NOT_PROVIDED = DiagnosticFactory1.create<PsiElement, KotlinType>(Severity.ERROR)

val BINDS_METHOD_NOT_ONE_PARAMETER = DiagnosticFactory0.create<PsiElement>(Severity.ERROR)
val BINDS_TYPE_IS_NOT_ASSIGNABLE = DiagnosticFactory0.create<PsiElement>(Severity.ERROR)
val BINDS_METHOD_NOT_ABSTRACT = DiagnosticFactory0.create<PsiElement>(Severity.ERROR)
val PROVIDES_METHOD_ABSTRACT = DiagnosticFactory0.create<PsiElement>(Severity.ERROR)

val MORE_THAN_ONE_INJECT_CONSTRUCTOR = DiagnosticFactory1.create<PsiElement, ClassDescriptor>(Severity.ERROR)
val NO_BINDINGS_FOUND = DiagnosticFactory1.create<PsiElement, KotlinType>(Severity.ERROR)
val AMBIGUOUS_BINDINGS = DiagnosticFactory2.create<PsiElement, KotlinType, List<DeclarationDescriptor>>(Severity.ERROR)
val BINDING_SCOPE_MISMATCH = DiagnosticFactory2.create<PsiElement, List<AnnotationDescriptor>, List<AnnotationDescriptor>>(Severity.ERROR)

object DaggerErrorMessages : DefaultErrorMessages.Extension {
    private val MAP = DiagnosticFactoryToRendererMap("Dagger")
    override fun getMap() = MAP

    init {
        MAP.put(
            COMPONENT_NOT_ABSTRACT,
            "Component must be an interface or an abstract class"
        )
        MAP.put(
            COMPONENT_TYPE_PARAMETER,
            "Component must not have type parameters"
        )
        MAP.put(
            COMPONENT_WITH_MULTIPLE_FACTORIES,
            "Component cannot have more than one factory definition: {0} found",
            DeclarationListRenderer()
        )
        MAP.put(
            COMPONENT_WITH_MULTIPLE_BUILDERS,
            "Component cannot have more than one builder definition: {0} found",
            DeclarationListRenderer()
        )
        MAP.put(
            COMPONENT_WITH_FACTORY_AND_BUILDER,
            "Component with factory {0} cannot have builders: {1}",
            DeclarationListRenderer(),
            DeclarationListRenderer()
        )
        MAP.put(
            MODULE_WITHOUT_ANNOTATION,
            "Module does not have module annotation (used in {0})",
            DeclarationRenderer()
        )
        MAP.put(
            FACTORY_WRONG_METHOD,
            "Factory should have single abstract method returning component type, found: {0}",
            DeclarationListRenderer()
        )
        MAP.put(
            FACTORY_DEPENDENCIES_NOT_DECLARED,
            "Factory dependency is not declared in the component annotation"
        )
        MAP.put(
            FACTORY_DEPENDENCIES_NOT_PROVIDED,
            "Factory does not provide dependency for type {0}",
            RENDER_TYPE
        )
        MAP.put(
            FACTORY_MODULE_NOT_PROVIDED,
            "Factory does not provide module instance {0}",
            RENDER_TYPE
        )
        MAP.put(
            BUILDER_WRONG_BUILD_METHOD,
            "Component builder must contain one build method, found: {0}",
            DeclarationListRenderer()
        )
        MAP.put(
            BUILDER_SETTER_TOO_MANY_PARAMS,
            "Component builder setter must contain only one parameter"
        )
        MAP.put(
            BUILDER_SETTER_WRONG_RETURN_TYPE,
            "Component builder setter must return Unit, builder type of builder supertype"
        )
        MAP.put(
            BUILDER_DEPENDENCIES_NOT_PROVIDED,
            "Builder does not provide dependency for type {0}",
            RENDER_TYPE
        )
        MAP.put(
            BUILDER_MODULE_NOT_PROVIDED,
            "Builder does not provide module instance {0}",
            RENDER_TYPE
        )
        MAP.put(
            MORE_THAN_ONE_INJECT_CONSTRUCTOR,
            "Class {0} has more than one @Inject annotated constructor",
            DeclarationRenderer()
        )
        MAP.put(
            NO_BINDINGS_FOUND,
            "No binding found for type {0}",
            RENDER_TYPE
        )
        MAP.put(
            AMBIGUOUS_BINDINGS,
            "More than one binding is found for type {0}: {1}",
            RENDER_TYPE,
            DeclarationListRenderer()
        )
        MAP.put(
            BINDING_SCOPE_MISMATCH,
            "Cannot use binding with different scope than component: binding - [{0}], component - [{1}]",
            AnnotationsRenderer(),
            AnnotationsRenderer()
        )
        MAP.put(
            BINDS_METHOD_NOT_ONE_PARAMETER,
            "Binds method must have only one parameter"
        )
        MAP.put(
            BINDS_METHOD_NOT_ONE_PARAMETER,
            "Binds method must have only one parameter"
        )
        MAP.put(
            BINDS_METHOD_NOT_ONE_PARAMETER,
            "Binds method parameter type must be assignable to return type"
        )
        MAP.put(
            BINDS_METHOD_NOT_ABSTRACT,
            "Binds method must be abstract"
        )
        MAP.put(
            PROVIDES_METHOD_ABSTRACT,
            "@Provides annotated method must not be abstract"
        )
    }
}

class DeclarationRenderer : DiagnosticParameterRenderer<DeclarationDescriptor> {
    override fun render(obj: DeclarationDescriptor, renderingContext: RenderingContext): String =
        obj.fqNameSafe.toString()
}

class DeclarationListRenderer : DiagnosticParameterRenderer<List<DeclarationDescriptor>> {
    override fun render(obj: List<DeclarationDescriptor>, renderingContext: RenderingContext): String =
        obj.joinToString { it.fqNameSafe.asString() }
}

class AnnotationsRenderer : DiagnosticParameterRenderer<List<AnnotationDescriptor>> {
    override fun render(list: List<AnnotationDescriptor>, renderingContext: RenderingContext): String =
        list.joinToString { it.fqName?.shortName()?.asString().orEmpty() }
}

