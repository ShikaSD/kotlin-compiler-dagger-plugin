package me.shika.test

import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.diagnostics.DiagnosticFactory0
import org.jetbrains.kotlin.diagnostics.Severity
import org.jetbrains.kotlin.diagnostics.rendering.DefaultErrorMessages
import org.jetbrains.kotlin.diagnostics.rendering.DiagnosticFactoryToRendererMap

internal fun MessageCollector.warn(message: Any?) =
    report(CompilerMessageSeverity.WARNING, message.toString())

val AMBIGUOUS_BINDING = DiagnosticFactory0.create<PsiElement>(Severity.ERROR)

object DaggerErrorMessages : DefaultErrorMessages.Extension {
    private val MAP = DiagnosticFactoryToRendererMap("Dagger")
    override fun getMap() = MAP

    init {
        MAP.put(
            AMBIGUOUS_BINDING,
            "Ambiguous binging"
        )
    }
}
