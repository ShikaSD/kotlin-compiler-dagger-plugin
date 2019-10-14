package me.shika.di

import me.shika.di.dagger.renderer.DaggerComponentRenderer
import me.shika.di.dagger.resolver.DaggerComponentDescriptor
import me.shika.di.dagger.resolver.ResolverContext
import me.shika.di.dagger.resolver.isComponent
import org.jetbrains.kotlin.analyzer.AnalysisResult
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.com.intellij.openapi.project.Project
import org.jetbrains.kotlin.container.ComponentProvider
import org.jetbrains.kotlin.container.get
import org.jetbrains.kotlin.context.ProjectContext
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.classRecursiveVisitor
import org.jetbrains.kotlin.resolve.BindingTrace
import org.jetbrains.kotlin.resolve.jvm.extensions.AnalysisHandlerExtension
import org.jetbrains.kotlin.resolve.lazy.ResolveSession
import java.io.File

class DiCompilerAnalysisExtension(
    private val sourcesDir: File,
    private val reporter: MessageCollector
) : AnalysisHandlerExtension {
    private var generatedFiles = false

    override fun doAnalysis(
        project: Project,
        module: ModuleDescriptor,
        projectContext: ProjectContext,
        files: Collection<KtFile>,
        bindingTrace: BindingTrace,
        componentProvider: ComponentProvider
    ): AnalysisResult? {
        if (generatedFiles) return null

        val resolveSession = componentProvider.get<ResolveSession>()
        val resolverContext = ResolverContext(module, bindingTrace, resolveSession)

        files.forEach { file ->
            val diagnosticCount = bindingTrace.bindingContext.diagnostics.all().size

            file.accept(
                classRecursiveVisitor { ktClass ->
                    val classDescriptor = resolveSession.resolveToDescriptor(ktClass) as ClassDescriptor
                    if (classDescriptor.isComponent()) {
                        val component = DaggerComponentDescriptor(
                            classDescriptor,
                            file,
                            resolverContext
                        )

                        if (bindingTrace.bindingContext.diagnostics.all().size != diagnosticCount) {
                            // No need to render, something is wrong with component
                            return@classRecursiveVisitor
                        }

                        val renderer = DaggerComponentRenderer(component, reporter)
                        renderer.render(sourcesDir)
                    }
                }
            )
        }

        generatedFiles = true
        return if (bindingTrace.bindingContext.diagnostics.isEmpty()) {
            AnalysisResult.RetryWithAdditionalRoots(
                bindingContext = bindingTrace.bindingContext,
                moduleDescriptor = module,
                additionalJavaRoots = emptyList(),
                additionalKotlinRoots = listOf(sourcesDir)
            )
        } else {
            AnalysisResult.compilationError(bindingTrace.bindingContext)
        }
    }

    override fun analysisCompleted(
        project: Project,
        module: ModuleDescriptor,
        bindingTrace: BindingTrace,
        files: Collection<KtFile>
    ): AnalysisResult? = null
}
