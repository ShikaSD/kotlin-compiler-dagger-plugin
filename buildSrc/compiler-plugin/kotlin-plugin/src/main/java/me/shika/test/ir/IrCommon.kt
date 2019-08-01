package me.shika.test.ir

import me.shika.test.TestCompilerIrGeneration
import org.jetbrains.kotlin.backend.common.BackendContext
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.descriptors.impl.ClassDescriptorImpl
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.addMember
import org.jetbrains.kotlin.ir.util.ConstantValueGenerator
import org.jetbrains.kotlin.ir.util.SymbolTable
import org.jetbrains.kotlin.ir.util.TypeTranslator
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.resolve.scopes.MemberScope
import org.jetbrains.kotlin.storage.LockBasedStorageManager

fun BackendContext.typeTranslator() = TypeTranslator(
    ir.symbols.externalSymbolTable,
    irBuiltIns.languageVersionSettings,
    builtIns
).apply {
    constantValueGenerator = ConstantValueGenerator(
        ir.irModule.descriptor,
        ir.symbols.externalSymbolTable
    )
    constantValueGenerator.typeTranslator = this
}

fun IrClass.addClass(symbolTable: SymbolTable, name: Name): IrClass {
    val cls = symbolTable.declareClass(
        startOffset,
        endOffset,
        TestCompilerIrGeneration.DiOrigin,
        ClassDescriptorImpl(
            descriptor,
            name,
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
    )

    this.addMember(cls)
    cls.parent = this

    return cls
}
