package me.shika.di

import me.shika.di.model.Binding
import me.shika.di.render.GraphToFunctionRenderer
import me.shika.di.resolver.COMPONENT_CALLS
import me.shika.di.resolver.ComponentDescriptor
import me.shika.di.resolver.ResolverContext
import me.shika.di.resolver.resolveGraph
import me.shika.di.resolver.resultType
import me.shika.di.resolver.validation.ExtractAnonymousTypes
import me.shika.di.resolver.validation.ExtractFunctions
import me.shika.di.resolver.validation.ParseParameters
import me.shika.di.resolver.validation.ReportBindingDuplicates
import org.jetbrains.kotlin.analyzer.AnalysisResult
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.com.intellij.openapi.project.Project
import org.jetbrains.kotlin.container.ComponentProvider
import org.jetbrains.kotlin.container.get
import org.jetbrains.kotlin.context.ProjectContext
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.resolve.BindingTrace
import org.jetbrains.kotlin.resolve.BodyResolver
import org.jetbrains.kotlin.resolve.TypeResolver
import org.jetbrains.kotlin.resolve.jvm.extensions.AnalysisHandlerExtension
import org.jetbrains.kotlin.resolve.lazy.ResolveSession
import org.jetbrains.kotlin.storage.LockBasedStorageManager
import java.io.File

class DiCompilerAnalysisExtension(
    private val sourcesDir: File,
    private val reporter: MessageCollector
) : AnalysisHandlerExtension {
    private var generatedFiles = false // fixme one more hack

    override fun doAnalysis(
        project: Project,
        module: ModuleDescriptor,
        projectContext: ProjectContext,
        files: Collection<KtFile>,
        bindingTrace: BindingTrace,
        componentProvider: ComponentProvider
    ): AnalysisResult? {
        return null

        val addedFiles = mutableListOf<File>()
        val resolveSession = componentProvider.get<ResolveSession>()
        val bodyResolver = componentProvider.get<BodyResolver>()
        val typeResolver = componentProvider.get<TypeResolver>()


        if (addedFiles.isEmpty()) return null

        generatedFiles = true
        return if (bindingTrace.bindingContext.diagnostics.isEmpty()) {
            AnalysisResult.RetryWithAdditionalRoots(
                bindingContext = bindingTrace.bindingContext,
                moduleDescriptor = module,
                additionalJavaRoots = emptyList(),
                additionalKotlinRoots = addedFiles
            ) // Repeat with my files pls
        } else {
            AnalysisResult.compilationError(bindingTrace.bindingContext)
        }
    }

    override fun analysisCompleted(
        project: Project,
        module: ModuleDescriptor,
        bindingTrace: BindingTrace,
        files: Collection<KtFile>
    ): AnalysisResult? {
        val calls = bindingTrace.getKeys(COMPONENT_CALLS)

        if (generatedFiles) {
            // record new descriptor
            return null
        }
        generatedFiles = true

        val processors = listOf(
            ParseParameters(),
            ExtractFunctions(),
            ExtractAnonymousTypes(),
            ReportBindingDuplicates()
        )

        calls.forEach { resolvedCall ->
            val context = ResolverContext(bindingTrace, LockBasedStorageManager.NO_LOCKS, resolvedCall)
            val resultType = resolvedCall.resultType

            val bindings = processors.fold(emptySequence<Binding>(), { bindings, processor ->
                with(processor) {
                    context.process(bindings)
                }
            })

            val descriptor = ComponentDescriptor(resultType!!, bindings.toList())
            val graph = context.resolveGraph(descriptor)

            println(graph)

            val fileSpec = GraphToFunctionRenderer(context).invoke(graph)
            fileSpec.writeTo(sourcesDir)
        }
        return AnalysisResult.RetryWithAdditionalRoots(
            bindingContext = bindingTrace.bindingContext,
            moduleDescriptor = module,
            additionalJavaRoots = emptyList(),
            additionalKotlinRoots = listOf(sourcesDir)
        ) // Repeat with my files pls
    }
}
