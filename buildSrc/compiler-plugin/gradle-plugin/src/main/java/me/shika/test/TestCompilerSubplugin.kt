package me.shika.test

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
class TestCompilerSubplugin: KotlinGradleSubplugin<AbstractCompile> {
    override fun apply(
        project: Project,
        kotlinCompile: AbstractCompile,
        javaCompile: AbstractCompile?,
        variantData: Any?,
        androidProjectHandler: Any?,
        kotlinCompilation: KotlinCompilation<KotlinCommonOptions>?
    ): List<SubpluginOption> {
        val extension = project.extensions.findByType(TestCompilerExtension::class.java) ?: TestCompilerExtension()
        val sourceSet = kotlinCompilation?.defaultSourceSet
        val sources = File(project.buildDir, "generated/source/di-compiler/${sourceSet?.name}/")
        sourceSet?.kotlin?.srcDir(sources)
        sourceSet?.kotlin?.exclude { it.file.startsWith(sources) }// FIXME tries to compile them

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

    override fun getCompilerPluginId(): String = "di-compiler-plugin"

    override fun getPluginArtifact(): SubpluginArtifact =
        SubpluginArtifact(
            groupId = "me.shika.test",
            artifactId = "di-kotlin-compiler-plugin",
            version = "0.0.1-SNAPSHOT"
        )

    override fun isApplicable(project: Project, task: AbstractCompile): Boolean =
        project.plugins.hasPlugin(TestCompilerPlugin::class.java)
}
