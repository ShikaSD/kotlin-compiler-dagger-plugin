package me.shika.di

import me.shika.di.resolver.COMPONENT_CALLS
import me.shika.di.resolver.COMPONENT_FUN_FQ_NAME
import me.shika.di.resolver.MODULE_FUN_FQ_NAME
import me.shika.di.resolver.MODULE_GET
import me.shika.di.resolver.classDescriptor
import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.descriptors.ValueParameterDescriptor
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.resolve.bindingContextUtil.getReferenceTargets
import org.jetbrains.kotlin.resolve.calls.checkers.CallChecker
import org.jetbrains.kotlin.resolve.calls.checkers.CallCheckerContext
import org.jetbrains.kotlin.resolve.calls.model.ResolvedCall
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe
import org.jetbrains.kotlin.resolve.scopes.receivers.ExpressionReceiver

class DiCompilerCallChecker : CallChecker {
    override fun check(resolvedCall: ResolvedCall<*>, reportOn: PsiElement, context: CallCheckerContext) {
        val bContext = context.trace.bindingContext
        if (resolvedCall.candidateDescriptor.fqNameSafe == COMPONENT_FUN_FQ_NAME) {
            context.trace.record(COMPONENT_CALLS, resolvedCall)
        }

        if (resolvedCall.candidateDescriptor.fqNameSafe == MODULE_FUN_FQ_NAME) {

        }

        if (resolvedCall.candidateDescriptor.fqNameSafe == MODULE_GET) {
            val receiver = (resolvedCall.extensionReceiver as ExpressionReceiver).expression
            val receiverRef = receiver.getReferenceTargets(bContext)
        }

        if (resolvedCall.hasModuleArgument()) {
            val modules = resolvedCall.valueArguments.filter { it.key.isModule() }
            println(modules)
        }
    }
}

fun ResolvedCall<*>.hasModuleArgument() =
    valueArguments.keys.any { it.isModule() }

fun ValueParameterDescriptor.isModule() =
    type.classDescriptor()?.fqNameSafe == FqName("lib.Module")
