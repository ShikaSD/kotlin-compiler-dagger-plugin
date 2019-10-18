package me.shika.di.dagger.renderer

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeName
import me.shika.di.dagger.resolver.classDescriptor
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
    ParameterSpec.builder(name, type, *modifiers.toTypedArray())
        .build()

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

fun ClassDescriptor.typeName() = if (isCompanionObject) {
    (containingDeclaration as ClassDescriptor).defaultType.typeName()
} else {
    defaultType.typeName()
}
