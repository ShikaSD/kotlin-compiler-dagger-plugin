package me.shika.di

import org.gradle.api.Plugin
import org.gradle.api.Project

class DiCompilerPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.extensions.create(
            "daggerCompiler",
            DiCompilerExtension::class.java
        )
    }
}

open class DiCompilerExtension {
    var enabled: Boolean = true
}
