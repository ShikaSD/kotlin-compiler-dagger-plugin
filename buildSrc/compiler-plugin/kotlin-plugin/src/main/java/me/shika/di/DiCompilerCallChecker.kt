package me.shika.di

import me.shika.di.resolver.COMPONENT_CALLS
import me.shika.di.resolver.COMPONENT_FUN_FQ_NAME
import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.resolve.calls.checkers.CallChecker
import org.jetbrains.kotlin.resolve.calls.checkers.CallCheckerContext
import org.jetbrains.kotlin.resolve.calls.model.ResolvedCall
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe

class DiCompilerCallChecker : CallChecker {
    override fun check(resolvedCall: ResolvedCall<*>, reportOn: PsiElement, context: CallCheckerContext) {
        if (resolvedCall.candidateDescriptor.fqNameSafe == COMPONENT_FUN_FQ_NAME) {
            context.trace.record(COMPONENT_CALLS, resolvedCall)
        }
    }
}
