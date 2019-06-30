package me.shika.test

import org.jetbrains.kotlin.analyzer.ModuleInfo
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.com.intellij.openapi.project.Project
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.descriptors.PackageFragmentDescriptor
import org.jetbrains.kotlin.descriptors.PackageFragmentProvider
import org.jetbrains.kotlin.incremental.components.LookupTracker
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.resolve.BindingTrace
import org.jetbrains.kotlin.resolve.jvm.extensions.PackageFragmentProviderExtension
import org.jetbrains.kotlin.storage.StorageManager

class TestCompilerPackageFragmentProviderExtension(val reporter: MessageCollector) : PackageFragmentProviderExtension {
    override fun getPackageFragmentProvider(
        project: Project,
        module: ModuleDescriptor,
        storageManager: StorageManager,
        trace: BindingTrace,
        moduleInfo: ModuleInfo?,
        lookupTracker: LookupTracker
    ): PackageFragmentProvider? = object : PackageFragmentProvider {
        private val packages = hashMapOf<FqName, Lazy<PackageFragmentDescriptor>>()

        override fun getPackageFragments(fqName: FqName): List<PackageFragmentDescriptor> {
//            reporter.warn("Got package fragments for $fqName")
            return listOfNotNull(packages[fqName]?.value)
        }

        override fun getSubPackagesOf(fqName: FqName, nameFilter: (Name) -> Boolean): Collection<FqName> {
            return packages.asSequence()
                .filter { (k, _) -> !k.isRoot && k.parent() == fqName }
                .mapTo(mutableListOf()) { it.key }
        }

    }

}
