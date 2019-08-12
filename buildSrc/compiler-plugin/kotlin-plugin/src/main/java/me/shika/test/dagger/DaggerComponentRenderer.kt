package me.shika.test.dagger

import me.shika.test.model.ResolveResult
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.descriptors.PackageFragmentDescriptor
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe
import org.jetbrains.kotlin.resolve.descriptorUtil.parents
import java.io.File

class DaggerComponentRenderer(
    val componentDescriptor: DaggerComponentDescriptor,
    val messageCollector: MessageCollector,
    val sourcesDir: File
) {
    private val packageFolder =
        componentDescriptor.definition.parents.find { it is PackageFragmentDescriptor }?.fqNameSafe

    init {
        sourcesDir.mkdirs()
        println(packageFolder)
    }

    fun render(results: List<ResolveResult>): List<File> = listOf(
//        fileOf(File(sourcesDir, "test.kt"), "\nclass Dagger${componentDescriptor.definition.name}")
    )

    fun fileOf(path: File, text: String) =
        path.also { path.writeText(text) }
}
