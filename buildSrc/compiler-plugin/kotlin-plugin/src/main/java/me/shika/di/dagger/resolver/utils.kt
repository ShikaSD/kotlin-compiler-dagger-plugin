package me.shika.di.dagger.resolver

import me.shika.di.DaggerErrorMessages
import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.contracts.parsing.isEqualsDescriptor
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.descriptors.annotations.AnnotationDescriptor
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.diagnostics.reportFromPlugin
import org.jetbrains.kotlin.js.resolve.diagnostics.findPsi
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.resolve.BindingTrace
import org.jetbrains.kotlin.resolve.DescriptorUtils
import org.jetbrains.kotlin.resolve.annotations.argumentValue
import org.jetbrains.kotlin.resolve.constants.KClassValue
import org.jetbrains.kotlin.resolve.descriptorUtil.annotationClass
import org.jetbrains.kotlin.resolve.scopes.DescriptorKindFilter
import org.jetbrains.kotlin.resolve.scopes.getDescriptorsFiltered
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.checker.KotlinTypeChecker
import org.jetbrains.kotlin.types.typeUtil.isInt

internal fun KotlinType.classDescriptor() = constructor.declarationDescriptor as? ClassDescriptor

internal fun DeclarationDescriptor.scopeAnnotations(): List<AnnotationDescriptor> =
    annotations.filter {
        it.annotationClass?.annotations?.hasAnnotation(SCOPE_FQ_NAME) == true
    }

internal fun DeclarationDescriptor.qualifiers(): List<AnnotationDescriptor> =
    annotations.filter {
        it.annotationClass?.annotations?.hasAnnotation(QUALIFIER_FQ_NAME) == true
    }

internal val SCOPE_FQ_NAME = FqName("javax.inject.Scope")
internal val QUALIFIER_FQ_NAME = FqName("javax.inject.Qualifier")
internal val INJECT_FQ_NAME = FqName("javax.inject.Inject")
internal val DAGGER_BINDS_INSTANCE_FQ_NAME = FqName("dagger.BindsInstance")

internal fun ClassDescriptor.allDescriptors(kindFilter: DescriptorKindFilter) =
    unsubstitutedMemberScope.getDescriptorsFiltered(kindFilter) { true }

private fun DeclarationDescriptor.isHashCodeDescriptor() =
    this is FunctionDescriptor && name == Name.identifier("hashCode")
            && returnType?.isInt() == true && valueParameters.isEmpty()

private fun DeclarationDescriptor.isToStringDescriptor() =
    this is FunctionDescriptor && name == Name.identifier("toString")
            && KotlinBuiltIns.isString(returnType) && valueParameters.isEmpty()

internal fun FunctionDescriptor.isFromAny() =
    isEqualsDescriptor() || isHashCodeDescriptor() || isToStringDescriptor()


internal fun DeclarationDescriptor.report(trace: BindingTrace, diagnostic: (PsiElement) -> Diagnostic) =
    findPsi()?.let { trace.reportFromPlugin(diagnostic(it), DaggerErrorMessages) }

internal fun AnnotationDescriptor.classListValue(context: ResolverContext, name: String) =
    (argumentValue(name)?.value as? List<KClassValue>)
        ?.mapNotNull {
            it.getArgumentType(context.module).classDescriptor()
        }
        ?: emptyList()

internal fun ClassDescriptor.isInstance() =
    !DescriptorUtils.isObject(this) && modality != Modality.ABSTRACT

internal infix fun KotlinType.applicableTo(type: KotlinType) =
    KotlinTypeChecker.DEFAULT.equalTypes(this, type)
