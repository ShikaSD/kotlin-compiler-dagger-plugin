package me.shika.di

import org.jetbrains.kotlin.analyzer.AnalysisResult
import org.jetbrains.kotlin.com.intellij.openapi.editor.Document
import org.jetbrains.kotlin.com.intellij.openapi.project.Project
import org.jetbrains.kotlin.com.intellij.psi.SingleRootFileViewProvider
import org.jetbrains.kotlin.container.ComponentProvider
import org.jetbrains.kotlin.container.get
import org.jetbrains.kotlin.context.ProjectContext
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.classOrObjectVisitor
import org.jetbrains.kotlin.resolve.BindingTrace
import org.jetbrains.kotlin.resolve.jvm.extensions.AnalysisHandlerExtension
import org.jetbrains.kotlin.resolve.lazy.ResolveSession

class TestExtAnalysisExtension: AnalysisHandlerExtension {

    var resolved = false

    override fun doAnalysis(
        project: Project,
        module: ModuleDescriptor,
        projectContext: ProjectContext,
        files: Collection<KtFile>,
        bindingTrace: BindingTrace,
        componentProvider: ComponentProvider
    ): AnalysisResult? {
        if (resolved) return null
        resolved = true

        val resolveSession = componentProvider.get<ResolveSession>()

        println("Start analysis")
        val updates = mutableListOf<Pair<KtFile, Pair<String, String>>>()
        files.forEach { file ->
            file.accept(
                classOrObjectVisitor {

                }
            )
        }

        files as MutableList<KtFile>
        files.replaceAll { original ->
            KtFile(
                object : SingleRootFileViewProvider(original.manager, original.virtualFile) {
                    private var isUpdated: Boolean = false
                    override fun getDocument(): Document? =
                        super.getDocument()?.let {
                            if (isUpdated) return@let it

                            isUpdated = true
                            val text = it.text

                            updates.filter { it.first === original }.forEach { (_, update) ->
                                it.setText(text.replace(update.first, update.second))
                            }

                            it
                        }
                },
                isCompiled = false
            )
        }

        return AnalysisResult.RetryWithAdditionalRoots(bindingTrace.bindingContext, module, emptyList(), emptyList())
    }

    override fun analysisCompleted(
        project: Project,
        module: ModuleDescriptor,
        bindingTrace: BindingTrace,
        files: Collection<KtFile>
    ): AnalysisResult? {
        println("Analysis completed")
        return super.analysisCompleted(project, module, bindingTrace, files)
    }
}
