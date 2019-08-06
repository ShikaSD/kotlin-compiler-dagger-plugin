package me.shika.test

import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector

internal fun MessageCollector.warn(message: Any?) =
    report(CompilerMessageSeverity.WARNING, message.toString())
