package me.shika.test

import org.gradle.api.Plugin
import org.gradle.api.Project

class TestCompilerPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.extensions.create(
            "diCompiler",
            TestCompilerExtension::class.java
        )
    }
}

open class TestCompilerExtension {
    var enabled: Boolean = true
}
