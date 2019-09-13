package me.shika.di

import me.shika.di.model.Binding
import me.shika.di.render.GraphToFunctionRenderer
import me.shika.di.resolver.COMPONENT_CALLS
import me.shika.di.resolver.ComponentDescriptor
import me.shika.di.resolver.GENERATED_CALL_NAME
import me.shika.di.resolver.ResolverContext
import me.shika.di.resolver.generateCallNames
import me.shika.di.resolver.resolveGraph
import me.shika.di.resolver.resultType
import me.shika.di.resolver.validation.ExtractAnonymousTypes
import me.shika.di.resolver.validation.ExtractFunctions
import me.shika.di.resolver.validation.ParseParameters
import me.shika.di.resolver.validation.ReportBindingDuplicates
import org.jetbrains.kotlin.analyzer.AnalysisResult
import org.jetbrains.kotlin.com.intellij.openapi.project.Project
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.resolve.BindingTrace
import org.jetbrains.kotlin.resolve.jvm.extensions.AnalysisHandlerExtension
import org.jetbrains.kotlin.storage.LockBasedStorageManager
import java.io.File

class DiCompilerAnalysisExtension(
    private val sourcesDir: File
) : AnalysisHandlerExtension {
    private var generatedFiles = false

    override fun analysisCompleted(
        project: Project,
        module: ModuleDescriptor,
        bindingTrace: BindingTrace,
        files: Collection<KtFile>
    ): AnalysisResult? {
        val calls = bindingTrace.getKeys(COMPONENT_CALLS)
        calls.generateCallNames(bindingTrace)
            .forEach { (call, name) ->
                bindingTrace.record(GENERATED_CALL_NAME, call, FqName(name))
            }

        if (generatedFiles) {
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
