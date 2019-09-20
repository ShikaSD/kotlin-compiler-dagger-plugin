package me.shika.di.resolver

import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.resolve.calls.model.ResolvedCall
import org.jetbrains.kotlin.util.slicedMap.Slices
import org.jetbrains.kotlin.util.slicedMap.WritableSlice

val COMPONENT_CALLS: WritableSlice<ResolvedCall<*>, Boolean> = Slices.createCollectiveSetSlice<ResolvedCall<*>>()
val GENERATED_CALL_NAME: WritableSlice<ResolvedCall<*>, FqName> = Slices.createSimpleSlice()

val MODULE_CALL_USAGES: WritableSlice<DeclarationDescriptor, MutableSet<ResolvedCall<*>>> = Slices.createSimpleSlice()
val MODULE_TYPE_RETRIEVAL: WritableSlice<DeclarationDescriptor, MutableSet<ResolvedCall<*>>> = Slices.createSimpleSlice()
val MODULE_DEFINITION: WritableSlice<DeclarationDescriptor, MutableSet<ResolvedCall<*>>> = Slices.createSimpleSlice()

internal val COMPONENT_FUN_FQ_NAME = FqName("lib.component")
internal val MODULE_FUN_FQ_NAME = FqName("lib.module")
internal val MODULE_GET = FqName("lib.get")
