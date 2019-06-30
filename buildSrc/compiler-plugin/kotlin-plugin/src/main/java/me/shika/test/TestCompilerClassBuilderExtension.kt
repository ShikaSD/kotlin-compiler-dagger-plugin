package me.shika.test

import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.codegen.ClassBuilder
import org.jetbrains.kotlin.codegen.ClassBuilderFactory
import org.jetbrains.kotlin.codegen.DelegatingClassBuilder
import org.jetbrains.kotlin.codegen.extensions.ClassBuilderInterceptorExtension
import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.diagnostics.DiagnosticSink
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.jvm.diagnostics.JvmDeclarationOrigin
import org.jetbrains.org.objectweb.asm.FieldVisitor
import org.jetbrains.org.objectweb.asm.MethodVisitor
import org.jetbrains.org.objectweb.asm.Opcodes

class TestCompilerClassBuilderExtension(private val reporter: MessageCollector): ClassBuilderInterceptorExtension {

    override fun interceptClassBuilderFactory(
        interceptedFactory: ClassBuilderFactory,
        bindingContext: BindingContext,
        diagnostics: DiagnosticSink
    ): ClassBuilderFactory =
        object : ClassBuilderFactory by interceptedFactory {
            override fun newClassBuilder(origin: JvmDeclarationOrigin): ClassBuilder =
                TestCompilerClassBuilder(
                    delegate = interceptedFactory.newClassBuilder(origin),
                    reporter = reporter
                )
        }

}

class TestCompilerClassBuilder(
    private val delegate: ClassBuilder,
    private val reporter: MessageCollector
): DelegatingClassBuilder() {
    init {
        reporter.report(CompilerMessageSeverity.WARNING, "Class builder loaded")
    }

    override fun getDelegate(): ClassBuilder = delegate

    override fun newMethod(origin: JvmDeclarationOrigin, access: Int, name: String, desc: String, signature: String?, exceptions: Array<out String>?): MethodVisitor {
//        reporter.report(CompilerMessageSeverity.WARNING, "visiting a method $name with descriptor ${origin.descriptor}")
        val delegateVisitor = super.newMethod(origin, access, name, desc, signature, exceptions)
        return object : MethodVisitor(Opcodes.ASM5, delegateVisitor) {
            override fun visitMethodInsn(opcode: Int, owner: String?, name: String?, descriptor: String?, isInterface: Boolean) {
//                reporter.report(CompilerMessageSeverity.WARNING, "visiting an instruction $name")
                super.visitMethodInsn(opcode, owner, name, descriptor, isInterface)
            }
        }
    }

    override fun newField(origin: JvmDeclarationOrigin, access: Int, name: String, desc: String, signature: String?, value: Any?): FieldVisitor {
//        reporter.report(CompilerMessageSeverity.WARNING, "visiting a field origin=$origin name=$name desc=$desc signature=$signature value=$value")
        return super.newField(origin, access, name, desc, signature, value)
    }

    override fun defineClass(origin: PsiElement?, version: Int, access: Int, name: String, signature: String?, superName: String, interfaces: Array<out String>) {
//        reporter.report(CompilerMessageSeverity.WARNING, "defining class $name")
        super.defineClass(origin, version, access, name, signature, superName, interfaces)
    }

    override fun visitSource(name: String, debug: String?) {
//        reporter.report(CompilerMessageSeverity.WARNING, "visiting source $name with debug $debug")
        super.visitSource(name, debug)
    }
}
