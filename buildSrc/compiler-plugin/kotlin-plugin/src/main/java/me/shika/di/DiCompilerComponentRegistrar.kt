package me.shika.di

import com.google.auto.service.AutoService
import me.shika.di.DiCommandLineProcessor.Companion.KEY_ENABLED
import me.shika.di.DiCommandLineProcessor.Companion.KEY_SOURCES
import org.jetbrains.kotlin.com.intellij.mock.MockProject
import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.compiler.plugin.ComponentRegistrar
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.container.StorageComponentContainer
import org.jetbrains.kotlin.container.useInstance
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.extensions.StorageComponentContainerContributor
import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.kotlin.resolve.calls.checkers.CallChecker
import org.jetbrains.kotlin.resolve.calls.checkers.CallCheckerContext
import org.jetbrains.kotlin.resolve.calls.model.KotlinCallKind
import org.jetbrains.kotlin.resolve.calls.model.KotlinResolutionCandidate
import org.jetbrains.kotlin.resolve.calls.model.ResolutionPart
import org.jetbrains.kotlin.resolve.calls.model.ResolvedCall
import org.jetbrains.kotlin.resolve.jvm.extensions.AnalysisHandlerExtension
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.checker.KotlinTypeChecker
import org.jetbrains.kotlin.types.checker.NewKotlinTypeChecker
import java.lang.reflect.Field
import java.lang.reflect.Modifier.FINAL


@AutoService(ComponentRegistrar::class)
class DiCompilerComponentRegistrar: ComponentRegistrar {
    override fun registerProjectComponents(project: MockProject, configuration: CompilerConfiguration) {
        if (configuration[KEY_ENABLED] != true) {
            return
        }
        val sourcesDir = configuration[KEY_SOURCES] ?: return

        sourcesDir.deleteRecursively()
        sourcesDir.mkdirs()
//        AnalysisHandlerExtension.registerExtension(
//            project,
//            DiCompilerAnalysisExtension(sourcesDir = sourcesDir)
//        )

        AnalysisHandlerExtension.registerExtension(
            project,
            TestExtAnalysisExtension()
        )

        StorageComponentContainerContributor.registerExtension(
            project,
            object : StorageComponentContainerContributor {
                override fun registerModuleComponents(
                    container: StorageComponentContainer,
                    platform: TargetPlatform,
                    moduleDescriptor: ModuleDescriptor
                ) {
                    container.useInstance(
                        object : CallChecker {
                            override fun check(
                                resolvedCall: ResolvedCall<*>,
                                reportOn: PsiElement,
                                context: CallCheckerContext
                            ) {
                                if (resolvedCall.candidateDescriptor == null) {
                                    println("Not resolved $resolvedCall")
                                }
                            }
                    })
                }
            }
        )


        replaceTypeChecker()
        addResolutionPart()
    }

    private fun addResolutionPart() {
        val cls = KotlinCallKind::class.java
        val sequence = cls.getDeclaredField("resolutionSequence")
        sequence.isAccessible = true
        sequence.set(KotlinCallKind.FUNCTION, listOf(
            object : ResolutionPart() {
                override fun KotlinResolutionCandidate.process(workIndex: Int) {
                    println(this)
                }
            },
            *KotlinCallKind.FUNCTION.resolutionSequence.toTypedArray()
        ))
    }

    private fun replaceTypeChecker() {
        val delegate = object : KotlinTypeChecker {
            override fun isSubtypeOf(p0: KotlinType, p1: KotlinType): Boolean =
                NewKotlinTypeChecker.Default.isSubtypeOf(p0, p1)

            override fun equalTypes(p0: KotlinType, p1: KotlinType): Boolean =
                NewKotlinTypeChecker.Default.equalTypes(p0, p1)
        }

        KotlinTypeChecker::class.java.getField("DEFAULT").setFinalStatic(delegate)
    }

    private fun Field.setFinalStatic(newValue: Any) {
        isAccessible = true

        val modifiersField = Field::class.java.getDeclaredField("modifiers")
        modifiersField.isAccessible = true
        modifiersField.setInt(this, modifiers and FINAL.inv())

        set(null, newValue)
    }
}
