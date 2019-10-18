package me.shika.di.dagger.renderer

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.Dynamic
import com.squareup.kotlinpoet.LambdaTypeName
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeVariableName
import com.squareup.kotlinpoet.WildcardTypeName
import me.shika.di.dagger.resolver.classDescriptor
import me.shika.di.model.Key
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe
import org.jetbrains.kotlin.types.KotlinType

internal fun <T> T?.ifNull(action: () -> T?): T? =
    this ?: action()

internal fun <T> T.letIf(condition: Boolean, block: (T) -> T) =
    if (condition) {
        block(this)
    } else {
        this
    }

internal fun PropertySpec.toParameter() =
    ParameterSpec.builder(name, type).build()

internal fun KotlinType.typeName(): TypeName? = classDescriptor()?.fqNameSafe?.let {
    val types = arguments.map { it.type.typeName()!! }
    val typed = ClassName(it.parent().asString(), it.shortName().asString())
        .let {
            if (types.isNotEmpty()) {
                with(ParameterizedTypeName) {
                    it.parameterizedBy(*types.toTypedArray())
                }
            } else {
                it
            }
        }
    typed.copy(nullable = this.isMarkedNullable)
}

internal fun ClassDescriptor.typeName(): TypeName? =
    if (isCompanionObject) {
        (containingDeclaration as ClassDescriptor).defaultType.typeName()
    } else {
        defaultType.typeName()
    }

internal fun TypeName.asString(): String =
    when (this) {
        is ClassName -> simpleName
        is ParameterizedTypeName -> rawType.simpleName + "_" + typeArguments.joinToString(separator = "_") { it.asString() }
        is WildcardTypeName,
        is Dynamic,
        is LambdaTypeName,
        is TypeVariableName -> TODO()
    } + ("_nullable".takeIf { isNullable } ?: "")

internal fun Key.parameterName(): String {
    val type = type.typeName()?.asString()?.decapitalize()
    val qualifiers = if (qualifiers.isNotEmpty()) {
        qualifiers.joinToString(separator = "_", prefix = "_") {
            it.type.typeName()?.asString().orEmpty().decapitalize()
        }
    } else {
        ""
    }
    return "$type$qualifiers"
}
