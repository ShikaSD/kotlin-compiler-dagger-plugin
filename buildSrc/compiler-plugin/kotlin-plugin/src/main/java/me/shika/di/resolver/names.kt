package me.shika.di.resolver

import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.resolve.calls.model.ResolvedCall
import org.jetbrains.kotlin.util.slicedMap.Slices
import org.jetbrains.kotlin.util.slicedMap.WritableSlice

val COMPONENT_CALLS: WritableSlice<ResolvedCall<*>, Boolean> = Slices.createCollectiveSetSlice<ResolvedCall<*>>()

internal val COMPONENT_FUN_FQ_NAME = FqName("lib.component")
