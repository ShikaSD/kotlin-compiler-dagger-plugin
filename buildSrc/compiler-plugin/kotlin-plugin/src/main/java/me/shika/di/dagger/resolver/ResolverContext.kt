package me.shika.di.dagger.resolver

import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.resolve.BindingTrace

class ResolverContext(
    val module: ModuleDescriptor,
//    val resolveSession: ResolveSession,
    val trace: BindingTrace
)
