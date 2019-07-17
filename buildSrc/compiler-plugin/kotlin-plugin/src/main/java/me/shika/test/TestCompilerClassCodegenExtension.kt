package me.shika.test

import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity.*
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.codegen.ExpressionCodegen
import org.jetbrains.kotlin.codegen.FunctionGenerationStrategy
import org.jetbrains.kotlin.codegen.ImplementationBodyCodegen
import org.jetbrains.kotlin.codegen.OwnerKind
import org.jetbrains.kotlin.codegen.StackValue
import org.jetbrains.kotlin.codegen.context.ClassContext
import org.jetbrains.kotlin.codegen.extensions.ExpressionCodegenExtension
import org.jetbrains.kotlin.codegen.writeSyntheticClassMetadata
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.descriptors.CallableMemberDescriptor.Kind.*
import org.jetbrains.kotlin.descriptors.annotations.Annotations
import org.jetbrains.kotlin.descriptors.impl.ClassConstructorDescriptorImpl
import org.jetbrains.kotlin.descriptors.impl.SimpleFunctionDescriptorImpl
import org.jetbrains.kotlin.descriptors.impl.ValueParameterDescriptorImpl
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.resolve.calls.model.ResolvedCall
import org.jetbrains.kotlin.resolve.descriptorUtil.builtIns
import org.jetbrains.kotlin.resolve.jvm.diagnostics.JvmDeclarationOrigin
import org.jetbrains.kotlin.resolve.jvm.diagnostics.JvmDeclarationOriginKind
import org.jetbrains.kotlin.resolve.jvm.diagnostics.OtherOrigin
import org.jetbrains.kotlin.resolve.jvm.jvmSignature.JvmMethodSignature
import org.jetbrains.org.objectweb.asm.Opcodes.*
import org.jetbrains.org.objectweb.asm.Type

class TestCompilerCodegenExtension(private val reporter: MessageCollector): ExpressionCodegenExtension {
    override val shouldGenerateClassSyntheticPartsInLightClassesMode: Boolean = true

    override fun applyFunction(receiver: StackValue, resolvedCall: ResolvedCall<*>, c: ExpressionCodegenExtension.Context): StackValue? {
        val name = resolvedCall.candidateDescriptor.name.asString()
        reporter.warn("Applying function $name")
        return super.applyFunction(receiver, resolvedCall, c)
    }

    override fun applyProperty(receiver: StackValue, resolvedCall: ResolvedCall<*>, c: ExpressionCodegenExtension.Context): StackValue? {
        reporter.report(WARNING, "Applying property ${resolvedCall.candidateDescriptor}")
        return super.applyProperty(receiver, resolvedCall, c)
    }

    override fun generateClassSyntheticParts(codegen: ImplementationBodyCodegen) {
        reporter.report(WARNING, "Generator called for ${codegen.className}")

        super.generateClassSyntheticParts(codegen)

        val descriptor = codegen.descriptor
        descriptor.generateConstructorAndFields(codegen)
        descriptor.generateBindings(codegen)
    }

    private fun ClassDescriptor.generateConstructorAndFields(codegen: ImplementationBodyCodegen) {
        val moduleInstances = codegen.bindingContext[DAGGER_MODULE_INSTANCES, this] ?: return
        reporter.warn("Generating constructor for $this")

        moduleInstances.forEach {
            codegen.v.newField(
                OtherOrigin(this),
                ACC_PRIVATE or ACC_SYNTHETIC,
                it.name.asString().decapitalize(),
                codegen.typeMapper.mapType(it.defaultType).descriptor,
                null,
                null
            )
        }

        val constructor = ClassConstructorDescriptorImpl.create(
            this,
            Annotations.EMPTY,
            false,
            source
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
            returnType = defaultType
        }

        codegen.generateFunction(
            constructor
        ) {
            val selfType = asmType(defaultType)

            v.load(0, selfType)
            v.invokespecial(selfType.internalName, "<init>", "()V", false)

            moduleInstances.forEachIndexed { index, it ->
                val name = it.name.asString().decapitalize()
                val type = asmType(it.defaultType)

                v.load(0, selfType)
                v.load(index + 1, type)
                v.putfield(selfType.internalName, name, type.descriptor)
            }

            v.areturn(Type.VOID_TYPE)
        }
    }

    private fun ClassDescriptor.generateBindings(codegen: ImplementationBodyCodegen) {
        val resolveResult = codegen.bindingContext[DAGGER_RESOLUTION_RESULT, this] ?: return
        val moduleInstances = codegen.bindingContext[DAGGER_MODULE_INSTANCES, this] ?: return

        val selfType = codegen.typeMapper.mapType(this)

        resolveResult.forEach { (binding, providers) ->
            val functionDescriptor = SimpleFunctionDescriptorImpl.create(
                this,
                Annotations.EMPTY,
                binding.name,
                SYNTHESIZED,
                binding.source
            ).apply {
                initialize(
                    null,
                    thisAsReceiverParameter,
                    mutableListOf(),
                    binding.valueParameters,
                    binding.returnType,
                    Modality.FINAL,
                    Visibilities.PUBLIC
                )
            }

            codegen.generateFunction(functionDescriptor) {
                val returnType = asmType(binding.returnType!!)

                val variableLength = providers.size
                reporter.warn("Repeating for $variableLength")

                (variableLength downTo 1).forEach {
                    val func = providers[it - 1]
                    val obj = func.containingDeclaration as ClassDescriptor
                    val returnType = asmType(func.returnType!!)
                    val moduleType = asmType(obj.defaultType)

                    if (moduleInstances.contains(obj)) {
                        v.load(0, selfType)
                        v.getfield(
                            selfType.internalName,
                            obj.name.asString().decapitalize(),
                            moduleType.descriptor
                        )
                    } else {
                        v.getstatic(
                            moduleType.internalName,
                            "INSTANCE",
                            moduleType.descriptor
                        )
                    }

                    val params = func.valueParameters.map { it.type }
                    val providerIndices = params.map { paramType ->
                        providers.indexOfFirst {
                            it.returnType?.constructor == paramType.constructor
                                    && it.returnType?.arguments == paramType.arguments
                        }
                    }

                    providerIndices.forEach {
                        v.load(it + 1, asmType(providers[it].returnType!!))
                    }

                    v.invokevirtual(
                        moduleType.internalName,
                        "${func.name}",
                        "(${params.joinToString("") { asmType(it).descriptor }})${returnType.descriptor}",
                        false
                    )

                    v.store(it, returnType)
                }

                v.load(1, returnType)
                v.areturn(returnType)
            }
        }
    }

    private fun ImplementationBodyCodegen.generateFunction(f: FunctionDescriptor, body: ExpressionCodegen.() -> Unit) {
        functionCodegen.generateMethod(
            OtherOrigin(
                myClass.psiOrParent,
                f
            ),
            f,
            object: FunctionGenerationStrategy.CodegenBased(state) {
                override fun doGenerateBody(gen: ExpressionCodegen, signature: JvmMethodSignature) {
                    gen.body()
                }
            }
        )
    }

//
//    val classDescriptor = ClassDescriptorImpl(
//        descriptor.containingDeclaration,
//        Name.identifier("Component"),
//        Modality.FINAL,
//        ClassKind.CLASS,
//        listOfNotNull(descriptor.classValueType),
//        descriptor.source,
//        false,
//        LockBasedStorageManager.NO_LOCKS
//    ).apply {
//        val constructor = ClassConstructorDescriptorImpl.createSynthesized(
//            this,
//            Annotations.EMPTY,
//            true,
//            source
//        ).initialize(emptyList(), Visibilities.PUBLIC)
//
//        initialize(
//            MemberScope.Empty,
//            setOf(constructor),
//            constructor
//        )
//    }

//        codegen.generateInnerClass(classDescriptor)

    private fun ImplementationBodyCodegen.generateMethodWithClassName() {
        val functionDescriptor = SimpleFunctionDescriptorImpl.create(
            descriptor,
            Annotations.EMPTY,
            Name.identifier("methodISee"),
            SYNTHESIZED,
            descriptor.source
        ).apply {
            initialize(
                null,
                descriptor.thisAsReceiverParameter,
                mutableListOf(),
                mutableListOf(),
                builtIns.stringType,
                Modality.FINAL,
                Visibilities.PUBLIC
            )
        }

        functionCodegen.generateMethod(OtherOrigin(myClass.psiOrParent, functionDescriptor), functionDescriptor, object: FunctionGenerationStrategy.CodegenBased(state) {
            override fun doGenerateBody(gen: ExpressionCodegen, signature: JvmMethodSignature) {
                val stringType = Type.getType(String::class.java)

                StackValue.constant(descriptor.name.asString(), stringType)
                    .put(stringType, gen.v)
                gen.v.areturn(stringType)
            }

        })
    }

    private fun ImplementationBodyCodegen.generateInnerClass(classDescriptor: ClassDescriptor) {
        val className = classDescriptor.name.asString()
        val currentAsmType = typeMapper.mapType(descriptor.defaultType)
        val testClassAsmType = Type.getObjectType(currentAsmType.internalName + "\$$className")

        val testClassBuilder = state.factory.newVisitor(
            JvmDeclarationOrigin(JvmDeclarationOriginKind.OTHER,  null, classDescriptor),
            Type.getObjectType(testClassAsmType.internalName),
            myClass.containingKtFile
        )
        val testClassContext = ClassContext(
            typeMapper, classDescriptor, OwnerKind.IMPLEMENTATION, context.parentContext, null
        )
        val testClassCodegen = ImplementationBodyCodegen(
            myClass, testClassContext, testClassBuilder, state, parentCodegen, false
        )

        testClassBuilder.defineClass(
            null, V1_6, ACC_PUBLIC or ACC_SUPER, testClassAsmType.internalName, null, "java/lang/Object", emptyArray()
        )
        v.visitInnerClass(testClassAsmType.internalName, currentAsmType.internalName, className, ACC_PUBLIC or ACC_STATIC)
        testClassCodegen.v.visitInnerClass(testClassAsmType.internalName, currentAsmType.internalName, className, ACC_PUBLIC or ACC_STATIC)
        writeSyntheticClassMetadata(testClassBuilder, state)

//        val expression = lambda?.let {
//            KtPsiUtil.deparenthesize(it.bodyExpression)
//        }

//        DescriptorFactory.createPrimaryConstructorForObject(testClassDescriptor, testClassDescriptor.source)
//            .apply {
//                returnType = testClassDescriptor.defaultType
//
//                val declarationOrigin = JvmDeclarationOrigin(JvmDeclarationOriginKind.OTHER, null, this)
//                testClassCodegen.functionCodegen.generateMethod(declarationOrigin, this, object : FunctionGenerationStrategy.CodegenBased(state) {
//                    override fun doGenerateBody(e: ExpressionCodegen, signature: JvmMethodSignature) = with(e) {
//                        v.load(0, testClassAsmType)
//                        v.invokespecial("java/lang/Object", "<init>", "()V", false)
//                        expression?.let { returnExpression(it) }
//                        v.areturn(Type.VOID_TYPE)
//                    }
//                })
//            }

        testClassBuilder.done()
    }
}
