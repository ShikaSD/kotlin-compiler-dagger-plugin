package me.shika.di

import me.shika.di.DiCommandLineProcessor.Companion.KEY_ENABLED
import me.shika.di.DiCommandLineProcessor.Companion.KEY_SOURCES
import org.jetbrains.kotlin.com.intellij.mock.MockProject
import org.jetbrains.kotlin.compiler.plugin.ComponentRegistrar
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.resolve.jvm.extensions.AnalysisHandlerExtension

//@AutoService(ComponentRegistrar::class)
class DiCompilerComponentRegistrar: ComponentRegistrar {
    override fun registerProjectComponents(project: MockProject, configuration: CompilerConfiguration) {
        if (configuration[KEY_ENABLED] != true) {
            return
        }
        val sourcesDir = configuration[KEY_SOURCES] ?: return

        sourcesDir.deleteRecursively()
        sourcesDir.mkdirs()

        AnalysisHandlerExtension.registerExtension(
            project,
            DiCompilerAnalysisExtension(sourcesDir = sourcesDir)
        )
    }

}
