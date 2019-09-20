package me.shika.di.resolver

import org.jetbrains.kotlin.backend.common.serialization.findTopLevelDescriptor
import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.incremental.components.NoLookupLocation
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.renderer.render
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.BindingTrace
import org.jetbrains.kotlin.resolve.calls.model.ResolvedCall
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe
import org.jetbrains.kotlin.types.KotlinType

fun KotlinType.classDescriptor() = constructor.declarationDescriptor as? ClassDescriptor

val ResolvedCall<*>.resultType get() = typeArguments[candidateDescriptor.typeParameters.single()]

fun ResolvedCall<*>.varargArguments() = valueArguments.values.first().arguments

fun Collection<ResolvedCall<*>>.generateCallNames(bindingTrace: BindingTrace): List<Pair<ResolvedCall<*>, String>> =
    map {
        val parent = it.parentDescriptor(bindingTrace)
        val pkg = parent?.fqNameSafe?.parent() ?: FqName.ROOT
        val typeName = it.resultType?.classDescriptor()?.name
        val functionName = COMPONENT_FUN_FQ_NAME.shortName()
        val name = Name.identifier("${parent?.name}_${functionName}_${typeName}_generated")
        it to pkg.child(name)
    }.groupBy { it.second }
        .flatMap { (_, values) ->
            values.mapIndexed { i, (call, value) ->
                call to value.render() + "_$i"
            }
        }

fun PsiElement.parentDeclaration(): KtDeclaration? {
    var declaration: PsiElement? = this
    while (declaration != null && declaration !is KtDeclaration) declaration = declaration.parent
    return declaration as? KtDeclaration
}

fun ResolvedCall<*>.parentDescriptor(trace: BindingTrace) =
    call.calleeExpression?.parentDeclaration()?.let {
        trace[BindingContext.DECLARATION_TO_DESCRIPTOR, it]
    }?.findTopLevelDescriptor()

fun ModuleDescriptor.findTopLevelFunctionForName(functionFqName: FqName): FunctionDescriptor? {
    val packageFqName = functionFqName.parent()
    val functionName = functionFqName.shortName()

    val packageViewDescriptor = getPackage(packageFqName)
    return packageViewDescriptor.memberScope.getContributedFunctions(
        functionName,
        NoLookupLocation.FROM_DESERIALIZATION
    ).firstOrNull { it.name == functionName }
}
