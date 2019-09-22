package me.shika.test.dagger

import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.ClassKind.CLASS
import org.jetbrains.kotlin.descriptors.ClassKind.INTERFACE
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.descriptors.Modality.ABSTRACT
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.resolve.scopes.DescriptorKindFilter
import org.jetbrains.kotlin.resolve.scopes.getDescriptorsFiltered

class DaggerFactoryDescriptor(
    private val componentDescriptor: DaggerComponentDescriptor
) {
    val factory = componentDescriptor.definition.innerClasses().single { it.annotations.hasAnnotation(DAGGER_FACTORY_ANNOTATION) }
    val factoryMethod = factory.methods().single { it.returnType == componentDescriptor.definition.defaultType }
    val instances = factoryMethod.valueParameters.filter { it.annotations.hasAnnotation(DAGGER_BINDS_INSTANCE) }
    val dependencies = factoryMethod.valueParameters.filterNot { it in instances }
}

private fun ClassDescriptor.innerClasses() =
    unsubstitutedMemberScope.getDescriptorsFiltered(DescriptorKindFilter.CLASSIFIERS) { true }
        .filterIsInstance<ClassDescriptor>()
        .filter { it.kind == INTERFACE || (it.kind == CLASS && it.modality == ABSTRACT) }

private fun ClassDescriptor.methods() =
    unsubstitutedMemberScope.getDescriptorsFiltered(DescriptorKindFilter.FUNCTIONS) { true }
        .filterIsInstance<FunctionDescriptor>()

private val DAGGER_FACTORY_ANNOTATION = FqName("dagger.Component.Factory")
private val DAGGER_BINDS_INSTANCE = FqName("dagger.BindsInstance")
