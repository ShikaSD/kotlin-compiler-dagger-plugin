package me.shika.test.ir

import me.shika.test.ir.TestCompilerIrGeneration.DiOrigin
import org.jetbrains.kotlin.backend.common.BackendContext
import org.jetbrains.kotlin.backend.common.lower.at
import org.jetbrains.kotlin.backend.common.lower.createIrBuilder
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.descriptors.CallableMemberDescriptor.Kind.SYNTHESIZED
import org.jetbrains.kotlin.descriptors.annotations.Annotations
import org.jetbrains.kotlin.descriptors.impl.ClassConstructorDescriptorImpl
import org.jetbrains.kotlin.descriptors.impl.SimpleFunctionDescriptorImpl
import org.jetbrains.kotlin.descriptors.impl.ValueParameterDescriptorImpl
import org.jetbrains.kotlin.ir.builders.*
import org.jetbrains.kotlin.ir.builders.declarations.addConstructor
import org.jetbrains.kotlin.ir.builders.declarations.addField
import org.jetbrains.kotlin.ir.builders.declarations.addFunction
import org.jetbrains.kotlin.ir.builders.declarations.addValueParameter
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrField
import org.jetbrains.kotlin.ir.declarations.addMember
import org.jetbrains.kotlin.ir.declarations.impl.IrValueParameterImpl
import org.jetbrains.kotlin.ir.expressions.IrDeclarationReference
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrGetField
import org.jetbrains.kotlin.ir.expressions.IrGetValue
import org.jetbrains.kotlin.ir.types.makeNullable
import org.jetbrains.kotlin.ir.types.toKotlinType
import org.jetbrains.kotlin.ir.util.*
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.TypeProjectionImpl

class ComponentIrGenerator(
    private val irClass: IrClass,
    private val moduleInstances: List<ClassifierDescriptor>,
    private val resolveResult: List<Pair<FunctionDescriptor, List<FunctionDescriptor>>>,
    private val scopedBindings: List<FunctionDescriptor>,
    private val symbolTable: SymbolTable = SymbolTable(),
    private val backendContext: BackendContext
) {
    private val externalSymbolTable = backendContext.ir.symbols.externalSymbolTable
    private val typeTranslator = backendContext.typeTranslator()
    private val scopedProviders = hashMapOf<IrField, IrClass>()

    fun KotlinType.toIrType() = typeTranslator.translateType(this)
    fun ClassifierDescriptor.bindingName() = Name.identifier(name.asString().decapitalize())
    fun FunctionDescriptor.providerName() = Name.identifier(name.asString().capitalize() + "Provider")
    fun FunctionDescriptor.providerFieldName() = Name.identifier(name.asString() + "Provider")

    fun generateFields() {
        moduleInstances.forEach { module ->
            irClass.addField {
                name = module.bindingName()
                type = module.defaultType.toIrType()
                isStatic = false
            }
        }

        if (scopedBindings.isEmpty()) return

        for (binding in scopedBindings) {
            val typeProjection = listOf(TypeProjectionImpl(binding.returnType!!))
            val bindingModule = binding.containingDeclaration as ClassDescriptor

            val providerIrClass = irClass.addClass(
                symbolTable,
                binding.providerName()
            ).also { cls ->
                symbolTable.withScope(cls.descriptor) {
                    cls.thisReceiver = symbolTable.declareValueParameter(
                        irClass.startOffset, irClass.endOffset, DiOrigin,
                        cls.descriptor.thisAsReceiverParameter,
                        cls.descriptor.defaultType.toIrType()
                    ).also {
                        it.parent = cls
                    }

                    val providerParameters = binding.valueParameters.mapTo(mutableListOf()) {
                        it.type.toIrType() to it.name
                    }
                    if (bindingModule in moduleInstances) {
                        val moduleName = bindingModule.bindingName()
                        val moduleType = bindingModule.defaultType.toIrType()

                        val moduleField = cls.addField {
                            name = moduleName
                            type = moduleType
                        }
                        cls.addConstructor {
                            returnType = cls.defaultType
                        }.also { ctor ->
                            val moduleParam = ctor.addValueParameter {
                                name = moduleName
                                type = moduleType
                                origin = DiOrigin
                            }.also { it.parent = ctor }

                            symbolTable.withScope(ctor.descriptor) {
                                ctor.body = backendContext.createIrBuilder(ctor.symbol).irBlockBody {
                                    callDefaultConstructor()
                                    +irSetField(
                                        irGet(cls.thisReceiver!!),
                                        moduleField,
                                        irGet(moduleParam)
                                    )
                                }
                            }
                        }
                    } else {
                        cls.addConstructor { returnType = cls.defaultType }.also { ctor ->
                            symbolTable.withScope(ctor.descriptor) {
                                ctor.body = backendContext.createIrBuilder(ctor.symbol).irBlockBody {
                                    callDefaultConstructor()
                                    +irReturnUnit()
                                }
                            }
                        }
                    }

                    val resultField = cls.addField {
                        name = Name.identifier("result")
                        type = binding.returnType!!.toIrType().makeNullable()
                    }

                    cls.addFunction(
                        "get",
                        binding.returnType!!.toIrType(),
                        isStatic = false
                    ).also { func ->
                        providerParameters.forEach {
                            func.addValueParameter(
                                it.second.asString(),
                                it.first,
                                DiOrigin
                            ).also {
                                it.parent = func
                            }
                        }

                        symbolTable.withScope(func.descriptor) {
                            func.body = backendContext.createIrBuilder(func.symbol).irBlockBody {
                                val receiver = irGet(cls.thisReceiver!!)
                                +irIfThen(
                                    context.irBuiltIns.unitType,
                                    condition = irEqualsNull(irGetField(receiver, resultField)),
                                    thenPart = irComposite {
                                        +irSetField(
                                            receiver,
                                            resultField,
                                            generateProvider(
                                                cls,
                                                binding,
                                                func.valueParameters.map { irGet(it) }
                                            )
                                        )
                                    }
                                )
                                +irReturn(irGetField(receiver, resultField))
                            }
                        }
                    }
                }
            }

            val providerField = irClass.addField {
                name = binding.providerFieldName()
                type = providerIrClass.defaultType
                isStatic = false
            }.also { providerField ->
                providerField.initializer = backendContext.createIrBuilder(providerField.symbol).run {
                    irExprBody(
                        irCallConstructor(
                            providerIrClass.constructors.first().symbol,
                            emptyList()
                        ).also { call ->
                            if (bindingModule in moduleInstances) {
                                call.putValueArgument(0, irGetField(
                                    irGet(irClass.thisReceiver!!),
                                    irClass.declarations.filterIsInstance<IrField>()
                                        .first { it.type.toKotlinType() == bindingModule.defaultType }
                                ))
                            }
                        }
                    )
                }
            }

            scopedProviders[providerField] = providerIrClass
        }
    }

    fun generateConstructor() {
        val ctorDescriptor = ClassConstructorDescriptorImpl.create(
            irClass.descriptor,
            Annotations.EMPTY,
            false,
            irClass.descriptor.source
        ).apply {
            initialize(
                moduleInstances.mapIndexed { index, it ->
                    ValueParameterDescriptorImpl(
                        containingDeclaration = this,
                        original = null,
                        index = index,
                        annotations = Annotations.EMPTY,
                        name = Name.identifier(it.name.asString().decapitalize()),
                        outType = it.defaultType,
                        declaresDefaultValue = false,
                        isCrossinline = false,
                        isNoinline = false,
                        varargElementType = null,
                        source = source
                    )
                },
                Visibilities.PUBLIC
            )
            returnType = irClass.descriptor.defaultType
        }

        val irCtor = symbolTable.declareConstructor(
            irClass.startOffset,
            irClass.endOffset,
            DiOrigin,
            ctorDescriptor
        ).apply {
            parent = irClass
            returnType = typeTranslator.translateType(descriptor.returnType)
            valueParameters.addAll(
                ctorDescriptor.valueParameters.map {
                    IrValueParameterImpl(
                        startOffset,
                        endOffset,
                        DiOrigin,
                        it,
                        typeTranslator.translateType(it.type),
                        null
                    ).also {
                        it.parent = this
                    }
                }
            )
            body = backendContext
                .createIrBuilder(symbol)
                .at(irClass)
                .irBlockBody(startOffset, endOffset) {
                    // Adding super call
                    val delegate = irClass.constructors.first()

                    // Add properties
                    moduleInstances.forEachIndexed { index, module ->
                        val prop = irClass.declarations.find {
                            it is IrField && it.name.asString() == module.name.asString().decapitalize()
                        } as IrField
                        val paramRef = this@apply.valueParameters[index]
                        +irSetField(irGet(irClass.thisReceiver!!), prop, irGet(paramRef))
                    }

                    +irDelegatingConstructorCall(delegate)
                }
        }
        irClass.addMember(irCtor)
    }

    fun generateBindings() {
        resolveResult.forEach { (binding, providers) ->
            val functionDescriptor = SimpleFunctionDescriptorImpl.create(
                irClass.descriptor,
                Annotations.EMPTY,
                binding.name,
                SYNTHESIZED,
                binding.source
            ).apply {
                initialize(
                    null,
                    irClass.descriptor.thisAsReceiverParameter,
                    mutableListOf(),
                    binding.valueParameters,
                    binding.returnType,
                    Modality.FINAL,
                    Visibilities.PUBLIC
                )
            }

            val irFunction = symbolTable.declareSimpleFunction(
                irClass.startOffset,
                irClass.endOffset,
                DiOrigin,
                functionDescriptor
            ).also {
                it.parent = irClass
                it.returnType = typeTranslator.translateType(functionDescriptor.returnType!!)
                it.dispatchReceiverParameter = irClass.thisReceiver

                it.body = backendContext.createIrBuilder(it.symbol)
                    .at(irClass)
                    .irBlockBody(irClass.startOffset, irClass.endOffset) {
                        generateBinding(providers, it.valueParameters.map { irGet(it) })
                    }
            }
            irClass.addMember(irFunction)
        }
    }

    private fun IrBlockBodyBuilder.generateBinding(
        providers: List<FunctionDescriptor>,
        parameters: List<IrDeclarationReference>
    ) {
        val providersLength = providers.size
        val params = parameters.toMutableList()

        (providersLength downTo 1).forEach {
            val provider = providers[it - 1]
            params += irGet(
                createTmpVariable(
                    if (provider in scopedBindings) {
                        generateScopedProvider(irClass, provider, params)
                    } else {
                        generateProvider(irClass, provider, params)
                    }
                )
            )
        }
        +irReturn(params.last())
    }

    private fun IrBlockBodyBuilder.generateProvider(
        cls: IrClass,
        provider: FunctionDescriptor,
        parameters: List<IrDeclarationReference>
    ): IrExpression {
        val call = when (provider) {
            is ClassConstructorDescriptor -> irCallConstructor(
                externalSymbolTable.referenceConstructor(provider),
                emptyList()
            )
            else -> irCall(
                externalSymbolTable.referenceDeclaredFunction(provider)
            ).also {
                val module = provider.containingDeclaration as ClassDescriptor
                it.dispatchReceiver = if (module in moduleInstances) {
                    irGetField(
                        irGet(cls.thisReceiver!!),
                        cls.declarations.find {
                            it is IrField && it.name == module.bindingName()
                        } as IrField
                    )
                } else {
                    irGetObject(
                        externalSymbolTable.referenceClass(module)
                    )
                }
            }
        }

        provider.valueParameters.map { value ->
            parameters.find {
                val type = when (it) {
                    is IrGetValue -> it.descriptor.type
                    is IrGetField -> it.descriptor.type
                    else -> null
                }
                type == value.type
            }
        }.forEachIndexed(call::putValueArgument)

        return call
    }

    private fun IrBlockBodyBuilder.generateScopedProvider(
        cls: IrClass,
        provider: FunctionDescriptor,
        parameters: List<IrDeclarationReference>
    ): IrExpression {
        val field = cls.declarations.find {
            it is IrField && it.name == provider.providerFieldName()
        } as IrField
        val getFunc = scopedProviders[field]?.functions?.find { it.name.asString() == "get" }
        return irCall(getFunc!!.symbol).also { call ->
            call.dispatchReceiver = irGetField(irGet(cls.thisReceiver!!), field)
            provider.valueParameters.map { value ->
                parameters.find {
                    val type = when (it) {
                        is IrGetValue -> it.descriptor.type
                        is IrGetField -> it.descriptor.type
                        else -> null
                    }
                    type == value.type
                }
            }.forEachIndexed(call::putValueArgument)
        }
    }

    private fun IrBlockBodyBuilder.callDefaultConstructor() {
        +irDelegatingConstructorCall(backendContext.irBuiltIns.anyClass.owner.constructors.single())
    }
}
