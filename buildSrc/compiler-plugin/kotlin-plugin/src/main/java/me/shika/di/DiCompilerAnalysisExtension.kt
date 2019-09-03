package me.shika.di

import me.shika.di.resolver.ResolverContext
import me.shika.di.resolver.classDescriptor
import org.jetbrains.kotlin.analyzer.AnalysisResult
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.com.intellij.openapi.project.Project
import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.container.ComponentProvider
import org.jetbrains.kotlin.container.get
import org.jetbrains.kotlin.context.ProjectContext
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtConstructor
import org.jetbrains.kotlin.psi.KtDeclarationWithBody
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
import org.jetbrains.kotlin.resolve.BindingTrace
import org.jetbrains.kotlin.resolve.BodyResolver
import org.jetbrains.kotlin.resolve.calls.callUtil.getResolvedCall
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe
import org.jetbrains.kotlin.resolve.descriptorUtil.getSuperInterfaces
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
        if (generatedFiles) return null

        val addedFiles = mutableListOf<File>()
        val resolveSession = componentProvider.get<ResolveSession>()
        val bodyResolver = componentProvider.get<BodyResolver>()
        val resolverContext = ResolverContext(module, bindingTrace, resolveSession)

        files.forEach { file ->
            file.accept(
                recursiveExpressionVisitor {
                    when (it) {
                        is KtCallExpression -> {
                            var declaration: PsiElement? = it
                            while ((declaration !is KtNamedFunction && declaration !is KtConstructor<*>) && declaration != null) {
                                declaration = declaration.parent
                            }
                            if (declaration == null) return@recursiveExpressionVisitor false
                            declaration as KtDeclarationWithBody

                            val descriptor = resolveSession.resolveToDescriptor(declaration)
                            if (descriptor is FunctionDescriptor) {
                                bodyResolver.resolveFunctionBody(
                                    resolveSession.declarationScopeProvider.getOuterDataFlowInfoForDeclaration(declaration),
                                    bindingTrace,
                                    declaration,
                                    descriptor,
                                    resolveSession.declarationScopeProvider.getResolutionScopeForDeclaration(declaration)
                                )
                            }
                            val call = it.getResolvedCall(bindingTrace.bindingContext)

                            // TODO: Check for correct descriptor

                            val callClass = call
                                ?.extensionReceiver
                                ?.type
                                ?.classDescriptor()

                            val isComponent = callClass?.getSuperInterfaces()
                                ?.any { it.fqNameSafe == FqName("lib.Component") } == true

                            if (!isComponent) return@recursiveExpressionVisitor false

                            val type = call!!.typeArguments[call.candidateDescriptor.typeParameters.first()]

                            println("Found exposed type $type")
                            true
                        }
                        else -> false
                    }
                }
            )
        }

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

        return null
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
