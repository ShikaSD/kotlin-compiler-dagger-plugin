package me.shika.test

import me.shika.test.dagger.*
import me.shika.test.resolver.ResolverContext
import org.jetbrains.kotlin.analyzer.AnalysisResult
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.com.intellij.openapi.project.Project
import org.jetbrains.kotlin.com.intellij.openapi.vfs.StandardFileSystems
import org.jetbrains.kotlin.com.intellij.openapi.vfs.VirtualFileManager
import org.jetbrains.kotlin.com.intellij.psi.PsiManager
import org.jetbrains.kotlin.com.intellij.psi.SingleRootFileViewProvider
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

class TestCompilerAnalysisExtension(
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
        val resolverContext = ResolverContext(module, bindingTrace, resolveSession)

        files.forEach { file ->
            file.accept(
                classRecursiveVisitor { ktClass ->
                    println("Visiting class ${ktClass.name}")
                    val classDescriptor = resolveSession.resolveToDescriptor(ktClass) as ClassDescriptor
                    if (classDescriptor.isComponent()) {
                        println("Class ${ktClass.name} is component")
                        val component = DaggerComponentDescriptor(
                            classDescriptor,
                            resolverContext
                        )
                        val bindings = DaggerBindingDescriptor(component)
                        val resolver = DaggerBindingResolver(reporter, bindings)
                        val renderer = DaggerComponentRenderer(component, reporter, sourcesDir)
                        addedFiles += renderer.render(resolver.resolve())
                    }
                }
            )
        }

//        if (addedFiles.isEmpty()) return null

        val fileManager = VirtualFileManager.getInstance()
        val localFS = fileManager.getFileSystem(StandardFileSystems.FILE_PROTOCOL)
        val psiManager = PsiManager.getInstance(project)
        val addedKtFiles = addedFiles.map {
            val virtualFile = localFS.findFileByPath(it.absolutePath)!!
            KtFile(
                viewProvider = SingleRootFileViewProvider(psiManager, virtualFile),
                isCompiled = false
            )
        }.toMutableList()

        files as MutableList<KtFile>
        files.addAll(addedKtFiles)

        generatedFiles = true

        return AnalysisResult.RetryWithAdditionalJavaRoots(bindingTrace.bindingContext, module, emptyList()) // Eat my files pls
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
