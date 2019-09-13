package me.shika.di

import me.shika.di.resolver.COMPONENT_CALLS
import me.shika.di.resolver.GENERATED_CALL_NAME
import me.shika.di.resolver.findTopLevelFunctionForName
import me.shika.di.resolver.varargArguments
import org.jetbrains.kotlin.codegen.JvmKotlinType
import org.jetbrains.kotlin.codegen.StackValue
import org.jetbrains.kotlin.codegen.extensions.ExpressionCodegenExtension
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
        val isComponentCall = c.codegen.bindingContext[COMPONENT_CALLS, resolvedCall] == true
        if (isComponentCall) {
            val module = c.codegen.context.functionDescriptor.module
            val function = module.findTopLevelFunctionForName(c.codegen.bindingContext[GENERATED_CALL_NAME, resolvedCall]!!)!!
            val clsType = c.typeMapper.mapType(function.returnType!!)

            val arguments = resolvedCall.varargArguments()
            function.valueParameters.zip(arguments).forEachIndexed { i, pair ->
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
                function,
                resolvedCall.dispatchReceiver,
                resolvedCall.extensionReceiver,
                resolvedCall.explicitReceiverKind,
                null,
                DelegatingBindingTrace(c.codegen.bindingContext, "test"),
                TracingStrategy.EMPTY,
                MutableDataFlowInfoForArguments.WithoutArgumentsCheck(DataFlowInfo.EMPTY)
            )

            resolvedCall.valueArguments.entries.first().value.arguments.forEachIndexed { index, valueArgument ->
                ctorResolvedCall.recordValueArgument(function.valueParameters[index], ExpressionValueArgument(valueArgument))
            }

            val method = c.typeMapper.mapToCallableMethod(function, false, resolvedCall = ctorResolvedCall)
            return StackValue.functionCall(clsType, function.returnType) {
                c.codegen.invokeMethodWithArguments(method, ctorResolvedCall, StackValue.none())
            }
        } else {
            return super.applyFunction(receiver, resolvedCall, c)
        }
    }
}
