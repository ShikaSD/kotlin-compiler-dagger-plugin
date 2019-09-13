package me.shika.di.render

import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import me.shika.di.model.Binding
import me.shika.di.model.GraphNode
import me.shika.di.resolver.GENERATED_CALL_NAME
import me.shika.di.resolver.ResolverContext
import me.shika.di.resolver.resultType
import me.shika.di.resolver.varargArguments
import org.jetbrains.kotlin.psi.ValueArgument
import org.jetbrains.kotlin.renderer.render

class GraphToFunctionRenderer(private val resolverContext: ResolverContext) : GraphRenderer {
    private val returnType = resolverContext.resolvedCall.resultType

    private val fqName = resolverContext.trace[GENERATED_CALL_NAME, resolverContext.resolvedCall]

    override fun invoke(node: GraphNode): FileSpec {
        val packageName = fqName?.parent()?.render().orEmpty()
        val name = fqName?.shortName()?.asString().orEmpty()
        val parameters = resolverContext.resolvedCall.varargArguments()
        return FileSpec.builder(packageName, name)
            .addFunction(
                FunSpec.builder(name)
                    .returns(returnType?.typeName()!!)
                    .addParameters(parameters.parameters(resolverContext.trace))
                    .addCode(
                        CodeBlock.builder()
                            .renderGraph(node, parameters)
                            .build()
                    )
                    .build()
            )
            .build()
    }
    
    private fun CodeBlock.Builder.renderGraph(node: GraphNode, parameters: List<ValueArgument>): CodeBlock.Builder {
        val parameterTypes = parameters.map { resolverContext.trace.getType(it.getArgumentExpression()!!)!! }
        val nodeSet = mutableSetOf<GraphNode>().apply { node.topSort(this) }
        nodeSet.forEach {
            val binding = it.value
            when (binding) {
                is Binding.Instance -> { /* Is a parameter directly */ }
                is Binding.Function -> {
                    val from = binding.from
                    val pIndex = parameterTypes.indexOfFirst { it.arguments.lastOrNull()?.type == binding.type }
                    val parameters = from.map {
                        val pIndex = parameterTypes.indexOf(it)
                        if (pIndex == -1) {
                            it.typeName().variableName()
                        } else {
                            "p$pIndex"
                        }
                    }
                    addStatement(
                        "val ${binding.type.typeName().variableName()} = p$pIndex(${parameters.joinToString()})"
                    )
                }
                is Binding.Constructor -> {
                    val from = binding.from
                    val parameters = from.map {
                        val pIndex = parameterTypes.indexOf(it)
                        if (pIndex == -1) {
                            it.typeName().variableName()
                        } else {
                            "p$pIndex"
                        }
                    }
                    addStatement(
                        "val ${binding.type.typeName().variableName()} = %T(${parameters.joinToString()})", binding.type.typeName()
                    )
                }
            }
        }

        addStatement("return ${node.value.type.typeName().variableName()}")
        
        return this
    }
}
