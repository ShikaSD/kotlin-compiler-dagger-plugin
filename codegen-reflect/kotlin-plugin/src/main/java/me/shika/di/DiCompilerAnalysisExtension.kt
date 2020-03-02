package me.shika.di

import me.shika.di.dagger.renderer.DaggerComponentRenderer
import me.shika.di.dagger.resolver.DaggerComponentDescriptor
import me.shika.di.dagger.resolver.ResolverContext
import me.shika.di.dagger.resolver.isComponent
import org.jetbrains.kotlin.analyzer.AnalysisResult
import org.jetbrains.kotlin.com.intellij.openapi.project.Project
import org.jetbrains.kotlin.container.ComponentProvider
import org.jetbrains.kotlin.container.get
import org.jetbrains.kotlin.context.ProjectContext
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtUserType
import org.jetbrains.kotlin.psi.classRecursiveVisitor
import org.jetbrains.kotlin.resolve.BindingTrace
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe
import org.jetbrains.kotlin.resolve.jvm.extensions.AnalysisHandlerExtension
import org.jetbrains.kotlin.resolve.lazy.ResolveSession
import java.io.File
import kotlin.time.ExperimentalTime
import kotlin.time.measureTimedValue

class DiCompilerAnalysisExtension(
    private val sourcesDir: File
) : AnalysisHandlerExtension {
    private var generatedFiles = false
    private var measure = 0L
    private var logger = File(sourcesDir.parentFile, "debug.log")
        .also { sourcesDir.parentFile.mkdirs(); it.createNewFile() }
        .bufferedWriter()

    @UseExperimental(ExperimentalTime::class)
    override fun doAnalysis(
        project: Project,
        module: ModuleDescriptor,
        projectContext: ProjectContext,
        files: Collection<KtFile>,
        bindingTrace: BindingTrace,
        componentProvider: ComponentProvider
    ): AnalysisResult? {
        measure = System.currentTimeMillis()
        if (generatedFiles) {
            return null
        }
        val resolveSession = componentProvider.get<ResolveSession>()

        sourcesDir.deleteRecursively()
        sourcesDir.mkdirs()

        val initialDiagnosticCount = bindingTrace.bindingContext.diagnostics.all().size
        val resolverContext = ResolverContext(module, bindingTrace)

        files.forEach { file ->
            val diagnosticCount = bindingTrace.bindingContext.diagnostics.all().size

            file.accept(
                classRecursiveVisitor { ktClass ->
                    val lexicalScope by lazy { resolveSession.declarationScopeProvider.getResolutionScopeForDeclaration(ktClass) }
                    ktClass.annotationEntries.find {
                        val userType = it.typeReference?.typeElement as? KtUserType ?: return@find false
                        val classifier = resolveSession.typeResolver.resolveClass(
                            lexicalScope,
                            userType,
                            bindingTrace,
                            false
                        )
                        classifier?.fqNameSafe == FqName("dagger.Component")
                    } ?: return@classRecursiveVisitor

                    val classDescriptor = resolveSession.resolveToDescriptor(ktClass) as? ClassDescriptor
                    if (classDescriptor?.isComponent() == true) {
                        val (component, descriptorCreateTime) = measureTimedValue {
                            DaggerComponentDescriptor(
                                classDescriptor,
                                resolverContext
                            )
                        }
                        log("${classDescriptor.name}: Descriptor setup taken ${descriptorCreateTime.inMilliseconds}ms")

                        if (bindingTrace.bindingContext.diagnostics.all().size != diagnosticCount) {
                            // No need to render, something is wrong with component
                            return@classRecursiveVisitor
                        }

                        val (_, renderTime) = measureTimedValue {
                            val renderer = DaggerComponentRenderer(component)
                            renderer.render(sourcesDir)
                        }
                        log("${classDescriptor.name}: Rendered component in $renderTime")
                    }
                }
            )

//            file.accept(
//                callExpressionRecursiveVisitor {
//                    it.getResolvedCall(bindingTrace.bindingContext)?.apply {
//                        if (!resultingDescriptor.annotations.hasAnnotation(FqName("dsl.dsl"))) return@apply
//
//                        val extensions = requiredExtensions(bindingTrace).toMutableList()
//                        val extensionParam = valueArguments.entries.findLast { (descriptor, _) ->
//                            descriptor.type.isExtensionFunctionType
//                        }
//                        extensionParam?.value?.arguments?.first()?.also {
//                            val dslBody = it.getArgumentExpression() ?: return@also
//                            dslBody.accept(
//                                lambdaExpressionVisitor {
//                                    it.bodyExpression?.acceptChildren(
//                                        callExpressionVisitor {
//                                            it.getResolvedCall(bindingTrace.bindingContext)?.apply {
//                                                println(resultingDescriptor)
//                                                extensions -= resultingDescriptor
//                                            }
//                                        }
//                                    )
//                                }
//                            )
//                        }
//                        println(extensions)
//                    }
//                }
//            )
        }

        generatedFiles = true
        log("Initial analysis in ${System.currentTimeMillis() - measure}ms")
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

    @UseExperimental(ExperimentalTime::class)
    override fun analysisCompleted(
        project: Project,
        module: ModuleDescriptor,
        bindingTrace: BindingTrace,
        files: Collection<KtFile>
    ): AnalysisResult? {
        log("Analysis completed in ${System.currentTimeMillis() - measure}ms")
        logger.flush()
        return null
    }
    
    private fun log(message: String) {
        logger.write(message)
        logger.newLine()
    }
}
