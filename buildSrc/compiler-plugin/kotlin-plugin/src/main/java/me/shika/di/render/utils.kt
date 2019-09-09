package me.shika.di.render

import com.squareup.kotlinpoet.ClassName
import me.shika.di.resolver.ResolverContext
import me.shika.di.resolver.classDescriptor
import org.jetbrains.kotlin.backend.common.serialization.findTopLevelDescriptor
import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.incremental.components.NoLookupLocation
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe
import org.jetbrains.kotlin.types.KotlinType

fun KotlinType.typeName(): ClassName? = classDescriptor()?.fqNameSafe?.let {
    ClassName(it.parent().asString(), it.shortName().asString())
}

fun PsiElement.parentDeclaration(): KtDeclaration? {
    var declaration: PsiElement? = this
    while (declaration != null && declaration !is KtDeclaration) declaration = declaration.parent
    return declaration as? KtDeclaration
}

fun ResolverContext.parentDescriptor() =
    resolvedCall.call.calleeExpression?.parentDeclaration()?.let {
        trace[BindingContext.DECLARATION_TO_DESCRIPTOR, it]
    }?.findTopLevelDescriptor()

fun ModuleDescriptor.findTopLevelFunctionForName(packageFqName: FqName, functionName: FqName): FunctionDescriptor? {
    val packageViewDescriptor = getPackage(packageFqName)
    val segments = functionName.pathSegments()
    val topLevelFunctions = packageViewDescriptor.memberScope.getContributedFunctions(
        segments.first(),
        NoLookupLocation.FROM_DESERIALIZATION
    ).filter { it.name == functionName.shortName() }
        .firstOrNull()
    return topLevelFunctions
}
