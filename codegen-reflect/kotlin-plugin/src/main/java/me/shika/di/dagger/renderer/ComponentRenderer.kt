package me.shika.di.dagger.renderer

import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.KModifier.INTERNAL
import com.squareup.kotlinpoet.KModifier.PRIVATE
import com.squareup.kotlinpoet.TypeSpec
import me.shika.di.dagger.renderer.creator.BuilderRenderer
import me.shika.di.dagger.renderer.creator.DefaultBuilderRenderer
import me.shika.di.dagger.renderer.creator.FactoryRenderer
import me.shika.di.dagger.renderer.dsl.markPrivate
import me.shika.di.dagger.renderer.dsl.primaryConstructor
import me.shika.di.dagger.renderer.dsl.property
import me.shika.di.dagger.resolver.DaggerComponentDescriptor
import me.shika.di.dagger.resolver.creator.BuilderDescriptor
import me.shika.di.dagger.resolver.creator.DefaultBuilderDescriptor
import me.shika.di.dagger.resolver.creator.FactoryDescriptor
import me.shika.di.dagger.resolver.scopeAnnotations
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.Visibilities
import org.jetbrains.kotlin.js.resolve.diagnostics.findPsi
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.renderer.render
import java.io.File

class DaggerComponentRenderer(
    private val componentDescriptor: DaggerComponentDescriptor
) {
    private val definition = componentDescriptor.definition
    private val componentClassName = ClassName(
        (definition.findPsi()?.containingFile as KtFile).packageFqName.render(),
        "Dagger${definition.name}"
    )

    fun render(sourcesDir: File) {
        val fileSpec = FileSpec.builder(componentClassName.packageName, componentClassName.simpleName)
            .addType(renderComponent(/*componentDescriptor.graph*/))
            .build()

        fileSpec.writeTo(sourcesDir)
    }

    private fun renderComponent(/*results: List<ResolveResult>*/): TypeSpec =
        TypeSpec.interfaceBuilder(componentClassName)
            .apply {
                if (definition.visibility == Visibilities.INTERNAL) {
                    addModifiers(INTERNAL)
                }
                extendComponent()
                if (componentDescriptor.creatorDescriptor is DefaultBuilderDescriptor) {
                    val modules = componentDescriptor.annotation.modules.mapNotNull { it.typeName() }
                    val dependencies = componentDescriptor.annotation.dependencies.mapNotNull { it.typeName() }
                    val scopes = componentDescriptor.definition.scopeAnnotations().mapNotNull { it.type.typeName() }
                    addAnnotation(
                        AnnotationSpec.builder(DAGGER_COMPONENT)
                            .addMember("modules = [${modules.joinToString { "%T::class" }}]", *modules.toTypedArray())
                            .addMember("dependencies = [${dependencies.joinToString { "%T::class" }}]", *dependencies.toTypedArray())
                            .build()
                    )

                    scopes.forEach {
                        addAnnotation(it as ClassName)
                    }
                }
                creator()
            }
            .build()

    private fun TypeSpec.Builder.extendComponent() = apply {
        if (definition.kind == ClassKind.INTERFACE) {
            addSuperinterface(definition.defaultType.typeName()!!)
        } else {
            superclass(definition.defaultType.typeName()!!)
        }
    }

    private fun TypeSpec.Builder.creator() = apply {
        val originalComponentClassName = definition.defaultType.typeName()!! as ClassName
        when (val descriptor = componentDescriptor.creatorDescriptor) {
            is FactoryDescriptor -> FactoryRenderer(
                originalComponentClassName,
                componentDescriptor.parameters,
                this
            ).render(descriptor)
            is BuilderDescriptor -> BuilderRenderer(
                originalComponentClassName,
                componentDescriptor.parameters,
                this
            ).render(descriptor)
            is DefaultBuilderDescriptor -> DefaultBuilderRenderer(
                originalComponentClassName,
                componentClassName,
                componentDescriptor.parameters,
                this
            ).render()
        }
    }

    private fun TypeSpec.Builder.addDependencyInstances() {
        val properties = componentDescriptor.parameters.map {
            val name = it.parameterName()
            property(name, it.type.typeName()!!) {
                markPrivate()
                initializer(name)
            }
        }

        primaryConstructor {
            addModifiers(PRIVATE)
            addParameters(properties.map { it.toParameter() })
        }
    }

    companion object {
       private val DAGGER_COMPONENT = ClassName("dagger", "Component")
    }
}
