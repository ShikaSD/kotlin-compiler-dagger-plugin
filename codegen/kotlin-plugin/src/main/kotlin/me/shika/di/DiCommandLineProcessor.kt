package me.shika.di

import org.jetbrains.kotlin.compiler.plugin.AbstractCliOption
import org.jetbrains.kotlin.compiler.plugin.CliOption
import org.jetbrains.kotlin.compiler.plugin.CommandLineProcessor
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.CompilerConfigurationKey
import java.io.File

//@AutoService(CommandLineProcessor::class)
class DiCommandLineProcessor : CommandLineProcessor {
    override val pluginId: String = "dagger-compiler-plugin"
    override val pluginOptions: Collection<AbstractCliOption> =
        listOf(
            CliOption(
                "enabled",
                "<true|false>",
                "Whether plugin is enabled",
                required = false
            ),
            CliOption(
                "sources",
                "<path>",
                "generated files folder",
                required = true
            )
        )

    override fun processOption(option: AbstractCliOption, value: String, configuration: CompilerConfiguration) {
        when (option.optionName) {
            "enabled" -> configuration.put(KEY_ENABLED, value.toBoolean())
            "sources" -> configuration.put(KEY_SOURCES, File(value))
        }
    }

    companion object {
        val KEY_ENABLED = CompilerConfigurationKey<Boolean>("di.plugin.enabled")
        val KEY_SOURCES = CompilerConfigurationKey<File>("di.plugin.sources")
    }
}
