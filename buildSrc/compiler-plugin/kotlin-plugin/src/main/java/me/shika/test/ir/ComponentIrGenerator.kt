package me.shika.test.ir

import me.shika.test.TestCompilerIrGeneration.DiOrigin
import me.shika.test.model.Binding
import me.shika.test.model.Endpoint
import me.shika.test.model.Injectable
import me.shika.test.model.ResolveResult
import org.jetbrains.kotlin.backend.common.BackendContext
import org.jetbrains.kotlin.backend.common.lower.at
import org.jetbrains.kotlin.backend.common.lower.createIrBuilder
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.ClassifierDescriptor
import org.jetbrains.kotlin.descriptors.Visibilities
import org.jetbrains.kotlin.descriptors.annotations.Annotations
import org.jetbrains.kotlin.descriptors.impl.ClassConstructorDescriptorImpl
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
import org.jetbrains.kotlin.ir.expressions.impl.IrSetFieldImpl
import org.jetbrains.kotlin.ir.types.makeNullable
import org.jetbrains.kotlin.ir.types.toKotlinType
import org.jetbrains.kotlin.ir.util.*
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.types.KotlinType

class ComponentIrGenerator(
    private val irClass: IrClass,
    private val moduleInstances: List<ClassifierDescriptor>,
    private val resolveResult: List<ResolveResult>,
    private val scopedBindings: Set<Binding>,
    private val symbolTable: SymbolTable = SymbolTable(),
    private val backendContext: BackendContext
) {
    private val externalSymbolTable = backendContext.ir.symbols.externalSymbolTable
    private val typeTranslator = backendContext.typeTranslator()
    private val scopedProviders = hashMapOf<IrField, IrClass>()
    private val scopedProviderFields = hashMapOf<Binding, IrField>()

    fun KotlinType.toIrType() = typeTranslator.translateType(this)
    fun ClassifierDescriptor.bindingName() = Name.identifier(name.asString().decapitalize())

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
            val bindingIrType = binding.type!!.toIrType()

            val providerIrClass = irClass.addClass(
                symbolTable,
                binding.providerName
            ).also { cls ->
                symbolTable.withScope(cls.descriptor) {
                    cls.thisReceiver = symbolTable.declareValueParameter(
                        irClass.startOffset, irClass.endOffset, DiOrigin,
                        cls.descriptor.thisAsReceiverParameter,
                        cls.descriptor.defaultType.toIrType()
                    ).also {
                        it.parent = cls
                    }

                    val providerParameters = binding.resolvedDescriptor.valueParameters.mapTo(mutableListOf()) {
                        it.type.toIrType() to it.name
                    }

                    cls.addConstructor {
                        returnType = cls.defaultType
                    }.also { ctor ->
                        symbolTable.withScope(ctor.descriptor) {
                            ctor.body = backendContext.createIrBuilder(ctor.symbol).irBlockBody {
                                callDefaultConstructor()

                                if (binding is Binding.InstanceFunction) {
                                    val bindingModule = binding.moduleInstance
                                    val moduleName = bindingModule.bindingName()
                                    val moduleType = bindingModule.defaultType.toIrType()

                                    val moduleField = cls.addField {
                                        name = moduleName
                                        type = moduleType
                                    }

                                    val moduleParam = ctor.addValueParameter {
                                        name = moduleName
                                        type = moduleType
                                        origin = DiOrigin
                                    }.also { it.parent = ctor }

                                    +irSetField(
                                        irGet(cls.thisReceiver!!),
                                        moduleField,
                                        irGet(moduleParam)
                                    )
                                }

                                +irReturnUnit()
                            }
                        }
                    }

                    val resultField = cls.addField {
                        name = Name.identifier("result")
                        type = bindingIrType.makeNullable()
                    }

                    cls.addFunction(
                        "get",
                        bindingIrType,
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
                                            generateBinding(
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
                name = Name.identifier(binding.providerName.asString().decapitalize())
                type = providerIrClass.defaultType
                isStatic = false
            }.also { providerField ->
                providerField.initializer = backendContext.createIrBuilder(providerField.symbol).run {
                    irExprBody(
                        irCallConstructor(
                            providerIrClass.constructors.first().symbol,
                            emptyList()
                        ).also { call ->
                            if (binding is Binding.InstanceFunction) {
                                call.putValueArgument(0, irGetField(
                                    irGet(irClass.thisReceiver!!),
                                    irClass.declarations.filterIsInstance<IrField>()
                                        .first { it.type.toKotlinType() == binding.moduleInstance.defaultType }
                                ))
                            }
                        }
                    )
                }
            }

            scopedProviders[providerField] = providerIrClass
            scopedProviderFields[binding] = providerField
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
        resolveResult.filter { it.endpoint is Endpoint.Exposed }.forEach { (endpoint, bindings) ->
            endpoint as Endpoint.Exposed
            irClass.addFunction(
                name = endpoint.source.name.asString(),
                returnType = endpoint.source.returnType!!.toIrType()
            ).also { func ->
                func.dispatchReceiverParameter = irClass.thisReceiver
                endpoint.source.valueParameters.forEach {
                    func.addValueParameter(
                        name = it.name.asString(),
                        type = it.type.toIrType()
                    ).also { it.parent = func }
                }

                func.body = backendContext.createIrBuilder(func.symbol).irBlockBody {
                    +irReturn(
                        generateBinding(bindings, func.valueParameters.map { irGet(it) })
                    )
                }
            }
        }

        resolveResult.filter { it.endpoint is Endpoint.Injected }
            .groupBy { (it.endpoint as Endpoint.Injected).source }
            .forEach { (function, results) ->
                irClass.addFunction(
                    name = function.name.asString(),
                    returnType = function.returnType!!.toIrType()
                ).also { func ->
                    func.dispatchReceiverParameter = irClass.thisReceiver
                    function.valueParameters.forEach {
                        func.addValueParameter(
                            name = it.name.asString(),
                            type = it.type.toIrType()
                        ).also { it.parent = func }
                    }

                    func.body = backendContext.createIrBuilder(func.symbol).irBlockBody {
                        val injectable = func.valueParameters[0]
                        results.forEach {
                            val endpoint = it.endpoint as Endpoint.Injected

                            val generatedBinding = generateBinding(it.bindings, emptyList())
                            when (endpoint.value) {
                                is Injectable.Property -> {
                                    val propertySymbol = externalSymbolTable.referenceField(endpoint.value.descriptor)

                                    +IrSetFieldImpl(
                                        startOffset, endOffset,
                                        propertySymbol,
                                        irGet(injectable),
                                        generatedBinding,
                                        context.irBuiltIns.unitType
                                    )
                                }
                                is Injectable.Setter -> {
                                    val setterSymbol = externalSymbolTable.referenceSimpleFunction(endpoint.value.descriptor)

                                    +irCall(setterSymbol).also {
                                        it.dispatchReceiver = irGet(injectable)
                                        it.putValueArgument(0, generatedBinding)
                                    }
                                }
                            }
                        }
                    }
                }
            }
    }

    private fun IrBlockBodyBuilder.generateBinding(
        bindings: Set<Binding>,
        parameters: List<IrDeclarationReference>
    ): IrDeclarationReference {
        val params = parameters.toMutableList()

        bindings.reversed().forEach { binding ->
            params += irGet(
                createTmpVariable(
                    if (binding in scopedBindings) {
                        generateScopedBinding(irClass, binding, params)
                    } else {
                        generateBinding(irClass, binding, params)
                    }
                )
            )
        }
        return params.last()
    }

    private fun IrBlockBodyBuilder.generateBinding(
        cls: IrClass,
        binding: Binding,
        parameters: List<IrDeclarationReference>
    ): IrExpression {
        val call = when (binding) {
            is Binding.Constructor -> irCallConstructor(
                externalSymbolTable.referenceConstructor(binding.descriptor),
                emptyList()
            )
            is Binding.StaticFunction -> irCall(
                externalSymbolTable.referenceDeclaredFunction(binding.descriptor)
            ).also {
                it.dispatchReceiver = irGetObject(
                    externalSymbolTable.referenceClass(it.descriptor.containingDeclaration as ClassDescriptor)
                )
            }
            is Binding.InstanceFunction -> irCall(
                externalSymbolTable.referenceDeclaredFunction(binding.descriptor)
            ).also {
                it.dispatchReceiver = irGetField(
                    irGet(cls.thisReceiver!!),
                    cls.declarations.find {
                        it is IrField && it.type.toKotlinType() == binding.moduleInstance.defaultType
                    } as IrField
                )
            }
        }

        binding.resolvedDescriptor.valueParameters.map { value ->
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

    private fun IrBlockBodyBuilder.generateScopedBinding(
        cls: IrClass,
        binding: Binding,
        parameters: List<IrDeclarationReference>
    ): IrExpression {
        val field = scopedProviderFields[binding]!!
        val getFunc = scopedProviders[field]?.functions?.find { it.name.asString() == "get" }
        return irCall(getFunc!!.symbol).also { call ->
            call.dispatchReceiver = irGetField(irGet(cls.thisReceiver!!), field)
            binding.resolvedDescriptor.valueParameters.map { value ->
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
