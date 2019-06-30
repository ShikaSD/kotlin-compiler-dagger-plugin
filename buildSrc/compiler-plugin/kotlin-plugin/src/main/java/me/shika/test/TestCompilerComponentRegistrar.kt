package me.shika.test

import me.shika.test.TestCommandLineProcessor.Companion.KEY_ENABLED
import com.google.auto.service.AutoService
import org.jetbrains.kotlin.cli.common.CLIConfigurationKeys
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.codegen.extensions.ClassBuilderInterceptorExtension
import org.jetbrains.kotlin.codegen.extensions.ExpressionCodegenExtension
import org.jetbrains.kotlin.com.intellij.mock.MockProject
import org.jetbrains.kotlin.compiler.plugin.ComponentRegistrar
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.extensions.StorageComponentContainerContributor
import org.jetbrains.kotlin.resolve.extensions.SyntheticResolveExtension
import org.jetbrains.kotlin.resolve.jvm.extensions.PackageFragmentProviderExtension

@AutoService(ComponentRegistrar::class)
class TestCompilerComponentRegistrar: ComponentRegistrar {
    override fun registerProjectComponents(project: MockProject, configuration: CompilerConfiguration) {
        val reporter = configuration.get(CLIConfigurationKeys.MESSAGE_COLLECTOR_KEY, MessageCollector.NONE)
        if (configuration[KEY_ENABLED] != true) {
            return
        }

        ClassBuilderInterceptorExtension.registerExtension(
            project,
            TestCompilerClassBuilderExtension(reporter)
        )

        ExpressionCodegenExtension.registerExtension(
            project,
            TestCompilerCodegenExtension(reporter)
        )

        PackageFragmentProviderExtension.registerExtension(
            project,
            TestCompilerPackageFragmentProviderExtension(reporter)
        )

        SyntheticResolveExtension.registerExtension(
            project,
            TestCompilerSyntheticResolveExtension(project, reporter)
        )

        StorageComponentContainerContributor.registerExtension(
            project,
            TestCompilerStorageComponentContributor(reporter)
        )
    }

}
