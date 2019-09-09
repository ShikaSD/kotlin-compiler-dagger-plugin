package me.shika.di.render

import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import me.shika.di.model.GraphNode
import me.shika.di.resolver.ResolverContext
import me.shika.di.resolver.classDescriptor
import me.shika.di.resolver.resultType
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe

class GraphToFunctionRenderer(private val resolverContext: ResolverContext) : GraphRenderer {
    private val returnType = resolverContext.resolvedCall.resultType

    override fun invoke(node: GraphNode): FileSpec =
        FileSpec.builder(packageName()?.asString().orEmpty(), "${fileName()?.asString()}_generated.kt")
            .addFunction(
                FunSpec.builder("component_${returnType?.classDescriptor()?.name}")
                    .returns(returnType?.typeName()!!)
                    .addCode("TODO()")
                    .build()
            )
            .build()

    private fun packageName() =
        resolverContext.parentDescriptor()?.fqNameSafe?.parent()

    private fun fileName() =
        resolverContext.parentDescriptor()?.name
}
