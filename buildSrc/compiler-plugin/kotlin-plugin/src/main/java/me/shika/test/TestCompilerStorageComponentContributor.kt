package me.shika.test

import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.container.StorageComponentContainer
import org.jetbrains.kotlin.container.useInstance
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.extensions.StorageComponentContainerContributor
import org.jetbrains.kotlin.resolve.TargetPlatform

class TestCompilerStorageComponentContributor(
    private val reporter: MessageCollector
) : StorageComponentContainerContributor {
    override fun registerModuleComponents(
        container: StorageComponentContainer,
        platform: TargetPlatform,
        moduleDescriptor: ModuleDescriptor
    ) {
        container.useInstance(TestCompilerDeclarationChecker(reporter))
    }
}
