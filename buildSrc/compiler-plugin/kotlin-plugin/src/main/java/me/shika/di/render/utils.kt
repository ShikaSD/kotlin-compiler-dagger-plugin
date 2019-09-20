package me.shika.di.render

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.Dynamic
import com.squareup.kotlinpoet.LambdaTypeName
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeVariableName
import com.squareup.kotlinpoet.WildcardTypeName
import me.shika.di.model.GraphNode
import me.shika.di.resolver.classDescriptor
import org.jetbrains.kotlin.psi.ValueArgument
import org.jetbrains.kotlin.resolve.BindingTrace
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe
import org.jetbrains.kotlin.types.KotlinType

fun KotlinType.typeName(): TypeName? = classDescriptor()?.fqNameSafe?.let {
    val types = arguments.map { it.type.typeName()!! }
    ClassName(it.parent().asString(), it.shortName().asString())
        .let {
            if (types.isNotEmpty()) {
                with(ParameterizedTypeName.Companion) {
                    it.parameterizedBy(*types.toTypedArray())
                }
            } else {
                it
            }
        }
}

fun TypeName?.variableName(): String =
    when (this) {
        is ClassName -> canonicalName.replace(".", "_")
        is ParameterizedTypeName -> {
            this.rawType.variableName() +
                this.typeArguments.joinToString(separator = "_", prefix = "_") { it.variableName() }
        }
        is TypeVariableName,
        is WildcardTypeName,
        is Dynamic,
        is LambdaTypeName,
        null -> TODO()
    }.decapitalize()

fun List<ValueArgument>.parameters(trace: BindingTrace): List<ParameterSpec> {
    return mapIndexed { i, arg ->
        val type = trace.getType(arg.getArgumentExpression()!!)
        val typeName = type!!.typeName()!!
        ParameterSpec.builder("p$i", typeName)
            .build()
    }
}

fun GraphNode.topSort(result: MutableSet<GraphNode>) {
    dependencies.forEach { it.topSort(result) }
    result.add(this)
}
