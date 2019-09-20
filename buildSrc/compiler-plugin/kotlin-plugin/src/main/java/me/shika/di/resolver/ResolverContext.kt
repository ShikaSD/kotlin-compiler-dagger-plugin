package me.shika.di.resolver

import org.jetbrains.kotlin.resolve.BindingTrace
import org.jetbrains.kotlin.resolve.calls.model.ResolvedCall
import org.jetbrains.kotlin.storage.StorageManager

class ResolverContext(
    val trace: BindingTrace,
    val storageManager: StorageManager,
    val resolvedCall: ResolvedCall<*>
)
