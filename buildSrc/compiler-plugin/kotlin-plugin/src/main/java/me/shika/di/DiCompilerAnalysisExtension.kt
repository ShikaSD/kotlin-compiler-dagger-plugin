package me.shika.di

import me.shika.di.resolver.ResolverContext
import org.jetbrains.kotlin.analyzer.AnalysisResult
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.com.intellij.openapi.project.Project
import org.jetbrains.kotlin.container.ComponentProvider
import org.jetbrains.kotlin.container.get
import org.jetbrains.kotlin.context.ProjectContext
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
import org.jetbrains.kotlin.resolve.BindingTrace
import org.jetbrains.kotlin.resolve.BodyResolver
import org.jetbrains.kotlin.resolve.TypeResolver
import org.jetbrains.kotlin.resolve.calls.model.VarargValueArgument
import org.jetbrains.kotlin.resolve.jvm.extensions.AnalysisHandlerExtension
import org.jetbrains.kotlin.resolve.lazy.ResolveSession
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
        val resolverContext = ResolverContext(module, bindingTrace, resolveSession)
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
        if (generatedFiles) return null
        generatedFiles = true

        calls.forEach { resolvedCall ->
            val resultType = resolvedCall.typeArguments[resolvedCall.candidateDescriptor.typeParameters.first()]
            val arguments = resolvedCall.valueArguments[resolvedCall.resultingDescriptor.valueParameters.first()] as VarargValueArgument
            val argumentTypes = arguments.arguments.map { bindingTrace.getType(it.getArgumentExpression()!!) }
            println("Found $resultType injection using $argumentTypes}")
        }
        return AnalysisResult.RetryWithAdditionalRoots(
            bindingContext = bindingTrace.bindingContext,
            moduleDescriptor = module,
            additionalJavaRoots = emptyList(),
            additionalKotlinRoots = emptyList()
        ) // Repeat with my files pls
    }
}

private fun recursiveExpressionVisitor(block: (KtExpression) -> Boolean) =
    object : KtTreeVisitorVoid() {
        override fun visitExpression(expression: KtExpression) {
            if (!block(expression)) {
                super.visitExpression(expression)
            }
        }
    }
