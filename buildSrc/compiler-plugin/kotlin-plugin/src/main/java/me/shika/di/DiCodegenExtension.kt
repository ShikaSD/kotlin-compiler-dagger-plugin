package me.shika.di

import me.shika.di.resolver.COMPONENT_CALLS
import org.jetbrains.kotlin.codegen.JvmKotlinType
import org.jetbrains.kotlin.codegen.StackValue
import org.jetbrains.kotlin.codegen.extensions.ExpressionCodegenExtension
import org.jetbrains.kotlin.descriptors.findClassAcrossModuleDependencies
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.resolve.DelegatingBindingTrace
import org.jetbrains.kotlin.resolve.calls.model.ExpressionValueArgument
import org.jetbrains.kotlin.resolve.calls.model.MutableDataFlowInfoForArguments
import org.jetbrains.kotlin.resolve.calls.model.ResolvedCall
import org.jetbrains.kotlin.resolve.calls.model.ResolvedCallImpl
import org.jetbrains.kotlin.resolve.calls.smartcasts.DataFlowInfo
import org.jetbrains.kotlin.resolve.calls.tasks.TracingStrategy
import org.jetbrains.kotlin.resolve.descriptorUtil.module

class DiCodegenExtension : ExpressionCodegenExtension {
    override fun applyFunction(
        receiver: StackValue,
        resolvedCall: ResolvedCall<*>,
        c: ExpressionCodegenExtension.Context
    ): StackValue? {
        val calls = c.codegen.bindingContext.getKeys(COMPONENT_CALLS)
        if (resolvedCall in calls) {
            println("Need to be overriden")
            val module = c.codegen.context.functionDescriptor.module
            val cls = module.findClassAcrossModuleDependencies(ClassId.fromString("Foo"))!!
            val clsType = c.typeMapper.mapType(cls)
            val ctor = cls.unsubstitutedPrimaryConstructor!!

            val arguments = resolvedCall.valueArguments.entries.first().value.arguments
            ctor.valueParameters.zip(arguments).forEachIndexed { i, pair ->
                val (param, argument) = pair
                val type = c.codegen.bindingContext.getType(argument.getArgumentExpression()!!)!!
                c.codegen.defaultCallGenerator.genValueAndPut(
                    param,
                    argument.getArgumentExpression()!!,
                    JvmKotlinType(c.typeMapper.mapType(type)),
                    i
                )
            }

            val ctorResolvedCall = ResolvedCallImpl(
                resolvedCall.call,
                ctor,
                resolvedCall.dispatchReceiver,
                resolvedCall.extensionReceiver,
                resolvedCall.explicitReceiverKind,
                null,
                DelegatingBindingTrace(c.codegen.bindingContext, "test"),
                TracingStrategy.EMPTY,
                MutableDataFlowInfoForArguments.WithoutArgumentsCheck(DataFlowInfo.EMPTY)
            )

            resolvedCall.valueArguments.entries.first().value.arguments.forEachIndexed { index, valueArgument ->
                ctorResolvedCall.recordValueArgument(ctor.valueParameters[index], ExpressionValueArgument(valueArgument))
            }

            return c.codegen.generateConstructorCall(ctorResolvedCall, clsType)
        } else {
            return super.applyFunction(receiver, resolvedCall, c)
        }
    }
}
