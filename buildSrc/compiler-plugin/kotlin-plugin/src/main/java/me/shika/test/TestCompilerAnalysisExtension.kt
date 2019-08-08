package me.shika.test

import org.jetbrains.kotlin.analyzer.AnalysisResult
import org.jetbrains.kotlin.com.intellij.openapi.editor.Document
import org.jetbrains.kotlin.com.intellij.openapi.project.Project
import org.jetbrains.kotlin.com.intellij.openapi.vfs.local.CoreLocalFileSystem
import org.jetbrains.kotlin.com.intellij.psi.PsiManager
import org.jetbrains.kotlin.com.intellij.psi.SingleRootFileViewProvider
import org.jetbrains.kotlin.container.ComponentProvider
import org.jetbrains.kotlin.context.ProjectContext
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.resolve.BindingTrace
import org.jetbrains.kotlin.resolve.jvm.extensions.AnalysisHandlerExtension
import java.io.File

class TestCompilerAnalysisExtension(val sourcesDir: File?) : AnalysisHandlerExtension {
    override fun doAnalysis(
        project: Project,
        module: ModuleDescriptor,
        projectContext: ProjectContext,
        files: Collection<KtFile>,
        bindingTrace: BindingTrace,
        componentProvider: ComponentProvider
    ): AnalysisResult? {
        val mutatedFiles = mutableListOf<Pair<KtFile, KtFile>>()
        val addedFiles = mutableListOf<KtFile>()
//        files.forEach { file ->
//            file.accept(
//                classRecursiveVisitor { ktClass ->
//                    val annotations = ktClass.annotationEntries.map {
//                        bindingTrace[BindingContext.ANNOTATION, it]
//                    }
//                    val componentAnnotation = annotations.find { it?.fqName == DAGGER_COMPONENT_ANNOTATION }
//                    val modules = componentAnnotation?.componentModules()
//                        ?.mapNotNull {
//                            val value = it.getArgumentType(module)
//                            value.constructor.declarationDescriptor as? ClassDescriptor
//                        }
//                    val moduleInstances = modules?.filter {
//                        !DescriptorUtils.isObject(it) && it.modality != Modality.ABSTRACT
//                    }
//                }
//            )
//        }

        sourcesDir?.mkdirs() ?: return null
        val testFile = File(sourcesDir, "testFile.kt")
        testFile.writeText("""
            |class TestGen {
            |   init {
            |       println("Debug me please!1")
            |   }
            |}
        """.trimMargin())
        val virtualFile = CoreLocalFileSystem().findFileByIoFile(testFile)!!

        addedFiles += KtFile(
            viewProvider = object : SingleRootFileViewProvider(PsiManager.getInstance(project), virtualFile) {
                var textUpdated = false
                override fun getDocument(): Document? = super.getDocument().also {
                    it?.also {
                        if (!textUpdated) {
                            textUpdated = true
//                                        it.setText("\n\nclass TestGen")
                        }
                    }
                }
            },
            isCompiled = false
        )

        files as MutableList<KtFile>
        mutatedFiles.forEach {
            val index = files.indexOf(it.first)
            if (index < 0) return@forEach
            files.removeAt(index)
            files.add(index, it.second)
        }

        files.addAll(addedFiles)

        return null
    }

    override fun analysisCompleted(
        project: Project,
        module: ModuleDescriptor,
        bindingTrace: BindingTrace,
        files: Collection<KtFile>
    ): AnalysisResult? {
        println("Analysis completed")
        return null
    }
}
