package me.shika.di

import me.shika.di.dagger.renderer.DaggerComponentRenderer
import me.shika.di.dagger.resolver.DaggerComponentDescriptor
import me.shika.di.dagger.resolver.ResolverContext
import me.shika.di.dagger.resolver.isComponent
import org.jetbrains.kotlin.analyzer.AnalysisResult
import org.jetbrains.kotlin.com.intellij.openapi.project.Project
import org.jetbrains.kotlin.container.ComponentProvider
import org.jetbrains.kotlin.context.ProjectContext
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.classRecursiveVisitor
import org.jetbrains.kotlin.resolve.BindingContext.CLASS
import org.jetbrains.kotlin.resolve.BindingTrace
import org.jetbrains.kotlin.resolve.jvm.extensions.AnalysisHandlerExtension
import java.io.File

class DiCompilerAnalysisExtension(
    private val sourcesDir: File
) : AnalysisHandlerExtension {
    private var generatedFiles = false

    override fun doAnalysis(
        project: Project,
        module: ModuleDescriptor,
        projectContext: ProjectContext,
        files: Collection<KtFile>,
        bindingTrace: BindingTrace,
        componentProvider: ComponentProvider
    ): AnalysisResult? = null

    override fun analysisCompleted(
        project: Project,
        module: ModuleDescriptor,
        bindingTrace: BindingTrace,
        files: Collection<KtFile>
    ): AnalysisResult? {
        if (generatedFiles) return null

        val initialDiagnosticCount = bindingTrace.bindingContext.diagnostics.all().size
        val resolverContext = ResolverContext(module, bindingTrace)

        files.forEach { file ->
            val diagnosticCount = bindingTrace.bindingContext.diagnostics.all().size

            file.accept(
                classRecursiveVisitor { ktClass ->
                    val classDescriptor = bindingTrace[CLASS, ktClass]
                    if (classDescriptor?.isComponent() == true) {
                        val component = DaggerComponentDescriptor(
                            classDescriptor,
                            file,
                            resolverContext
                        )

                        if (bindingTrace.bindingContext.diagnostics.all().size != diagnosticCount) {
                            // No need to render, something is wrong with component
                            return@classRecursiveVisitor
                        }

                        val renderer = DaggerComponentRenderer(component)
                        renderer.render(sourcesDir)
                    }
                }
            )
        }

        generatedFiles = true
        return if (bindingTrace.bindingContext.diagnostics.all().size == initialDiagnosticCount) {
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
}
