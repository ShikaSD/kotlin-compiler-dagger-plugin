package me.shika.test

import com.google.auto.service.AutoService
import org.jetbrains.kotlin.compiler.plugin.AbstractCliOption
import org.jetbrains.kotlin.compiler.plugin.CliOption
import org.jetbrains.kotlin.compiler.plugin.CommandLineProcessor
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.CompilerConfigurationKey
import java.io.File

@AutoService(CommandLineProcessor::class)
class TestCommandLineProcessor : CommandLineProcessor {
    override val pluginId: String = "di-compiler-plugin"
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
                "generated files folder",
                "generated files folder",
                required = false
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
