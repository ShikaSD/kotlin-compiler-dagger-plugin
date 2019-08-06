package me.shika.test

import org.jetbrains.kotlin.analyzer.AnalysisResult
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.com.intellij.openapi.project.Project
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.classRecursiveVisitor
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.BindingTrace
import org.jetbrains.kotlin.resolve.jvm.extensions.AnalysisHandlerExtension

class TestCompilerAnalysisExtension(val reporter: MessageCollector) : AnalysisHandlerExtension {
    override fun analysisCompleted(
        project: Project,
        module: ModuleDescriptor,
        bindingTrace: BindingTrace,
        files: Collection<KtFile>
    ): AnalysisResult? {
        files.forEach { file ->
            file.accept(
                classRecursiveVisitor { ktClass ->
                    println(
                        ktClass to bindingTrace[BindingContext.ANNOTATION, ktClass.annotationEntries.firstOrNull()]?.fqName
                    )
                }
            )
        }

        return AnalysisResult.Companion.success(bindingTrace.bindingContext, module)
    }
}
