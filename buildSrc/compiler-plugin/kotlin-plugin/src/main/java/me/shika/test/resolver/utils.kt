package me.shika.test.resolver

import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.types.KotlinType

fun KotlinType.classDescriptor() = constructor.declarationDescriptor as? ClassDescriptor
