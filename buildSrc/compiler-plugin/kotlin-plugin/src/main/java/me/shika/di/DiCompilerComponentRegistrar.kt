package me.shika.di

import com.google.auto.service.AutoService
import me.shika.di.DiCommandLineProcessor.Companion.KEY_ENABLED
import me.shika.di.DiCommandLineProcessor.Companion.KEY_SOURCES
import org.jetbrains.kotlin.cli.common.CLIConfigurationKeys
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.com.intellij.mock.MockProject
import org.jetbrains.kotlin.compiler.plugin.ComponentRegistrar
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.JVMConfigurationKeys.IR
import org.jetbrains.kotlin.extensions.CompilerConfigurationExtension
import org.jetbrains.kotlin.resolve.jvm.extensions.AnalysisHandlerExtension

@AutoService(ComponentRegistrar::class)
class DiCompilerComponentRegistrar: ComponentRegistrar {
    override fun registerProjectComponents(project: MockProject, configuration: CompilerConfiguration) {
        val reporter = configuration.get(CLIConfigurationKeys.MESSAGE_COLLECTOR_KEY, MessageCollector.NONE)
        if (configuration[KEY_ENABLED] != true) {
            return
        }
        val sourcesDir = configuration[KEY_SOURCES] ?: return

        sourcesDir.deleteRecursively()
        sourcesDir.mkdirs()

        CompilerConfigurationExtension.registerExtension(
            project,
            object : CompilerConfigurationExtension {
                override fun updateConfiguration(configuration: CompilerConfiguration) {
                    configuration.put(IR, false)
                }
            })

        AnalysisHandlerExtension.registerExtension(
            project,
            DiCompilerAnalysisExtension(sourcesDir = sourcesDir, reporter = reporter)
        )
    }

}
