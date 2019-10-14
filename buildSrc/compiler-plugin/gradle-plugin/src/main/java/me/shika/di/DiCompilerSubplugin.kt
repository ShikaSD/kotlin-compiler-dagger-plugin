package me.shika.di

import com.google.auto.service.AutoService
import org.gradle.api.Project
import org.gradle.api.tasks.compile.AbstractCompile
import org.jetbrains.kotlin.gradle.dsl.KotlinCommonOptions
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinGradleSubplugin
import org.jetbrains.kotlin.gradle.plugin.SubpluginArtifact
import org.jetbrains.kotlin.gradle.plugin.SubpluginOption
import java.io.File

@AutoService(KotlinGradleSubplugin::class)
class DiCompilerSubplugin: KotlinGradleSubplugin<AbstractCompile> {
    override fun apply(
        project: Project,
        kotlinCompile: AbstractCompile,
        javaCompile: AbstractCompile?,
        variantData: Any?,
        androidProjectHandler: Any?,
        kotlinCompilation: KotlinCompilation<KotlinCommonOptions>?
    ): List<SubpluginOption> {
        val extension = project.extensions.findByType(DiCompilerExtension::class.java) ?: DiCompilerExtension()

        val sourceSetName = if (variantData != null) {
            // Lol
            variantData.javaClass.getMethod("getName").run {
                isAccessible = true
                invoke(variantData) as String
            }
        } else {
            if (kotlinCompilation == null) error("In non-Android projects, Kotlin compilation should not be null")
            kotlinCompilation.compilationName
        }

        val sources = File(project.buildDir, "generated/source/di-compiler/$sourceSetName/")
        kotlinCompilation?.allKotlinSourceSets?.forEach {
            it.kotlin.srcDir(sources)
            it.kotlin.exclude { it.file.startsWith(sources) }
        }

        // Lol #2
        variantData?.javaClass?.methods?.first { it.name =="addJavaSourceFoldersToModel" }?.apply {
            isAccessible = true
            invoke(variantData, sources)
        }

        return listOf(
            SubpluginOption(
                key = "enabled",
                value = extension.enabled.toString()
            ),
            SubpluginOption(
                key = "sources",
                value = sources.absolutePath
            )
        )
    }

    override fun getCompilerPluginId(): String = "dagger-compiler-plugin"

    override fun getPluginArtifact(): SubpluginArtifact =
        SubpluginArtifact(
            groupId = "me.shika.di",
            artifactId = "dagger-kotlin-compiler-plugin",
            version = "0.0.1-preview"
        )

    override fun isApplicable(project: Project, task: AbstractCompile): Boolean =
        project.plugins.hasPlugin(DiCompilerPlugin::class.java)
}
