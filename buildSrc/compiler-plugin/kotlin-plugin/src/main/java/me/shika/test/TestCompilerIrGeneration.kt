package me.shika.test

import org.jetbrains.kotlin.backend.common.BackendContext
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.lower.at
import org.jetbrains.kotlin.backend.common.lower.createIrBuilder
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.descriptors.CallableMemberDescriptor.Kind.DECLARATION
import org.jetbrains.kotlin.descriptors.CallableMemberDescriptor.Kind.SYNTHESIZED
import org.jetbrains.kotlin.descriptors.annotations.Annotations
import org.jetbrains.kotlin.descriptors.impl.*
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.builders.*
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.declarations.impl.IrValueParameterImpl
import org.jetbrains.kotlin.ir.expressions.IrGetValue
import org.jetbrains.kotlin.ir.expressions.impl.IrDelegatingConstructorCallImpl
import org.jetbrains.kotlin.ir.util.*
import org.jetbrains.kotlin.ir.visitors.IrElementVisitorVoid
import org.jetbrains.kotlin.ir.visitors.acceptChildrenVoid
import org.jetbrains.kotlin.ir.visitors.acceptVoid
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.descriptorUtil.module
import org.jetbrains.kotlin.resolve.scopes.DescriptorKindFilter
import org.jetbrains.kotlin.resolve.scopes.MemberScope
import org.jetbrains.kotlin.storage.LockBasedStorageManager
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.KotlinTypeFactory
import org.jetbrains.kotlin.types.TypeProjectionImpl

class TestCompilerIrGeneration(val reporter: MessageCollector): IrGenerationExtension {
    override fun generate(file: IrFile, backendContext: BackendContext, bindingContext: BindingContext) {
        file.acceptVoid(object : IrElementVisitorVoid {
            override fun visitElement(element: IrElement) {
                element.acceptChildrenVoid(this)
            }

            override fun visitClass(declaration: IrClass) {
                generate(declaration, backendContext, bindingContext)
                declaration.acceptChildrenVoid(this)
            }

        })
    }

    private fun generate(irClass: IrClass, backendContext: BackendContext, bindingContext: BindingContext) {
        reporter.warn("Calling generator for ${irClass.descriptor}")

        val descriptor = irClass.descriptor

        val moduleInstances = bindingContext[DAGGER_MODULE_INSTANCES, descriptor] ?: return
        val resolveResult = bindingContext[DAGGER_RESOLUTION_RESULT, descriptor] ?: return
        // TODO Cache in declaration checker
        val scopedBindings = (resolveResult.flatMap { it.second } + resolveResult.map { it.first })
            .distinct()
            .filter { it.scopeAnnotations().isNotEmpty() }

        val symbolTable = SymbolTable()
        val typeTranslator = TypeTranslator(
            backendContext.ir.symbols.externalSymbolTable,
            backendContext.irBuiltIns.languageVersionSettings,
            descriptor.module.builtIns
        ).apply {
            constantValueGenerator = ConstantValueGenerator(descriptor.module, backendContext.ir.symbols.externalSymbolTable)
            constantValueGenerator.typeTranslator = this
        }

        irClass.generateFields(moduleInstances, scopedBindings, symbolTable, typeTranslator, backendContext)
        irClass.generateConstructor(moduleInstances, symbolTable, typeTranslator, backendContext)
        irClass.generateBindings(resolveResult, moduleInstances, symbolTable, typeTranslator, backendContext)
    }

    private fun IrClass.generateFields(
        moduleInstances: List<ClassifierDescriptor>,
        scopedBindings: List<FunctionDescriptor>,
        symbolTable: SymbolTable,
        typeTranslator: TypeTranslator,
        backendContext: BackendContext
    ) {
        val irClass = this
        moduleInstances.forEach { module ->
            val field = symbolTable.declareField(
                startOffset,
                endOffset,
                DiOrigin,
                PropertyDescriptorImpl.create(
                    descriptor,
                    Annotations.EMPTY,
                    Modality.FINAL,
                    Visibilities.PRIVATE,
                    false,
                    Name.identifier(module.name.asString().decapitalize()),
                    DECLARATION,
                    descriptor.source,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false
                ).also {
                    it.initialize(null, null)
                    it.setType(
                        module.defaultType,
                        emptyList(),
                        descriptor.thisAsReceiverParameter,
                        null
                    )
                },
                typeTranslator.translateType(module.defaultType)
            ).also {
                it.parent = this
            }
            addMember(field)
        }

        if (scopedBindings.isEmpty()) return

        val providerClass = irClass.descriptor.module.findClassAcrossModuleDependencies(
            ClassId.topLevel(FqName("javax.inject.Provider"))
        )!!

        for (binding in scopedBindings) {
            val providerInstance = symbolTable.declareClass(
                startOffset, endOffset, DiOrigin,
                ClassDescriptorImpl(
                    descriptor,
                    Name.identifier(binding.name.asString().capitalize()),
                    Modality.FINAL,
                    ClassKind.CLASS,
                    emptyList(),
                    descriptor.source,
                    false,
                    LockBasedStorageManager("test")
                ).also { classDesc ->
                    classDesc.initialize(
                        MemberScope.Empty,
                        emptySet(),
                        null
                    )
                }
            ).also { cls ->
                val typeProjection = listOf(TypeProjectionImpl(binding.returnType!!))
                val providerType = KotlinTypeFactory.simpleType(
                    annotations = Annotations.EMPTY,
                    constructor = providerClass.typeConstructor,
                    arguments = typeProjection,
                    nullable = false
                )
                cls.superTypes += typeTranslator.translateType(providerType)
                symbolTable.withScope(cls.descriptor) {
                    cls.thisReceiver = symbolTable.declareValueParameter(
                        startOffset, endOffset, DiOrigin,
                        cls.descriptor.thisAsReceiverParameter,
                        typeTranslator.translateType(cls.descriptor.defaultType)
                    )

                    symbolTable.declareSimpleFunction(
                        startOffset, endOffset, DiOrigin,
                        SimpleFunctionDescriptorImpl.create(
                            cls.descriptor,
                            Annotations.EMPTY,
                            Name.identifier("get"),
                            SYNTHESIZED,
                            cls.descriptor.source
                        ).also {
                            it.initialize(
                                null,
                                cls.descriptor.thisAsReceiverParameter,
                                emptyList(),
                                emptyList(),
                                binding.returnType,
                                Modality.OPEN,
                                Visibilities.PUBLIC
                            )
                        }
                    ).also { func ->
                        cls.addMember(func)
                        func.parent = cls
                        func.returnType = typeTranslator.translateType(func.descriptor.returnType!!)
                        func.dispatchReceiverParameter = cls.thisReceiver
                        providerClass.getMemberScope(typeProjection)
                            .getContributedDescriptors(DescriptorKindFilter.FUNCTIONS) { true }
                            .filter { it.name.asString() == "get" }
                            .filterIsInstance<FunctionDescriptor>()
                            .mapTo(func.overriddenSymbols) {
                                backendContext.ir.symbols.externalSymbolTable.referenceSimpleFunction(it)
                            }
                        symbolTable.withScope(func.descriptor) {
                            func.body = backendContext.createIrBuilder(func.symbol).irBlockBody {
                                irReturnUnit()
                            }
                        }
                    }
                }
            }
            addMember(providerInstance)
        }
    }

    private fun KotlinType.name() =
        constructor.declarationDescriptor?.name?.asString()?.decapitalize()

    private fun IrClass.generateConstructor(
        moduleInstances: List<ClassifierDescriptor>,
        symbolTable: SymbolTable,
        typeTranslator: TypeTranslator,
        backendContext: BackendContext
    ) {
        val irClass = this

        val ctorDescriptor = ClassConstructorDescriptorImpl.create(
            descriptor,
            Annotations.EMPTY,
            false,
            descriptor.source
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
            returnType = descriptor.defaultType
        }

        val irCtor = symbolTable.declareConstructor(
            startOffset,
            endOffset,
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
//                    val anyConstructor = backendContext.builtIns.any.constructors.single()
//                    +IrDelegatingConstructorCallImpl(
//                        startOffset, endOffset,
//                        backendContext.irBuiltIns.unitType,
//                        backendContext.ir.symbols.externalSymbolTable.referenceConstructor(anyConstructor),
//                        anyConstructor
//                    )
                    val delegate = irClass.constructors.first().descriptor
                    +IrDelegatingConstructorCallImpl(
                        startOffset,
                        endOffset,
                        backendContext.irBuiltIns.unitType,
                        backendContext.ir.symbols.externalSymbolTable.referenceConstructor(delegate),
                        delegate
                    )

                    // Add properties
                    moduleInstances.forEachIndexed { index, module ->
                        val prop = declarations.find {
                            it is IrField && it.name.asString() == module.name.asString().decapitalize()
                        } as IrField
                        val paramRef = this@apply.valueParameters[index]
                        +irSetField(irGet(thisReceiver!!), prop, irGet(paramRef))
                    }
                }
        }
        addMember(irCtor)
    }

    private fun IrClass.generateBindings(
        resolveResult: List<Pair<FunctionDescriptor, List<FunctionDescriptor>>>,
        moduleInstances: List<ClassifierDescriptor>,
        symbolTable: SymbolTable,
        typeTranslator: TypeTranslator,
        backendContext: BackendContext
    ) {
        val irClass = this

        fun IrBlockBodyBuilder.resolveProvider(
            provider: FunctionDescriptor,
            params: List<IrGetValue>,
            module: ClassDescriptor
        ) = irGet(
            createTmpVariable(
                irCall(
                    backendContext.ir.symbols.externalSymbolTable.referenceDeclaredFunction(provider)
                ).also { call ->
                    call.dispatchReceiver =
                        if (module in moduleInstances) {
                            irGetField(
                                irGet(
                                    irClass.defaultType,
                                    irClass.thisReceiver!!.symbol
                                ),
                                declarations.find {
                                    it is IrField && it.name.asString() == module.name.asString().decapitalize()
                                } as IrField
                            )
                        } else {
                            irGetObject(
                                backendContext.ir.symbols.externalSymbolTable.referenceClass(module)
                            )
                        }
                    provider.valueParameters.map { value ->
                        params.find { it.descriptor.type == value.type }
                    }.forEachIndexed(call::putValueArgument)
                }
            )
        )

        resolveResult.forEach { (binding, providers) ->
            val functionDescriptor = SimpleFunctionDescriptorImpl.create(
                descriptor,
                Annotations.EMPTY,
                binding.name,
                SYNTHESIZED,
                binding.source
            ).apply {
                initialize(
                    null,
                    descriptor.thisAsReceiverParameter,
                    mutableListOf(),
                    binding.valueParameters,
                    binding.returnType,
                    Modality.FINAL,
                    Visibilities.PUBLIC
                )
            }

            val irFunction = symbolTable.declareSimpleFunction(
                startOffset,
                endOffset,
                DiOrigin,
                functionDescriptor
            ).also {
                it.parent = this
                it.returnType = typeTranslator.translateType(functionDescriptor.returnType!!)
                it.dispatchReceiverParameter = thisReceiver

                it.body = backendContext.createIrBuilder(it.symbol)
                    .at(this)
                    .irBlockBody(startOffset, endOffset) {
                        val params = it.valueParameters.mapTo(mutableListOf()) { irGet(it) }
                        val providersLength = providers.size
                        (providersLength downTo 1).forEach {
                            val provider = providers[it - 1]
                            val module = provider.containingDeclaration as ClassDescriptor

                            if (provider is ClassConstructorDescriptor) {
                                params += irGet(
                                    createTmpVariable(
                                        irCallConstructor(
                                            backendContext.ir.symbols.externalSymbolTable.referenceConstructor(provider),
                                            emptyList()
                                        ).also {
                                            provider.valueParameters.map { value ->
                                                params.find { it.descriptor.type == value.type }
                                            }.forEachIndexed(it::putValueArgument)
                                        }
                                    )
                                )
                            } else {
                                params += resolveProvider(provider, params, module)
                            }
                        }
                        +irReturn(params.last())
                    }
            }
            addMember(irFunction)
        }
    }

    object DiOrigin : IrDeclarationOriginImpl("DI")
}
