package me.shika.test.dagger.resolver

import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.resolve.BindingTrace
import org.jetbrains.kotlin.resolve.lazy.ResolveSession

class ResolverContext(
    val module: ModuleDescriptor,
    val trace: BindingTrace,
    val resolveSession: ResolveSession
)
