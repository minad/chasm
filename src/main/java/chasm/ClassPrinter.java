package chasm;

import java.io.IOException;
import java.io.Writer;
import java.util.HashMap;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.Attribute;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.ModuleVisitor;
import org.objectweb.asm.Type;
import org.objectweb.asm.TypePath;
import org.objectweb.asm.util.Printer;
import static org.objectweb.asm.Opcodes.*;

public final class ClassPrinter extends ClassVisitor implements ClassOutput {
    private static final String[] OPCODES;
    static {
        OPCODES = new String[Printer.OPCODES.length];
        for (int i = 0; i < Printer.OPCODES.length; ++i)
            OPCODES[i] = Printer.OPCODES[i].toLowerCase();
    }

    private final class ModulePrinter extends ModuleVisitor {
        ModulePrinter() {
            super(Pipeline.API);
        }

        @Override
        public void visitMainClass(final String mainClass) {
            p.block("mainclass");
            p.sym(mainClass);
            p.endLine();
        }

        @Override
        public void visitPackage(final String pkg) {
            p.block("package");
            p.sym(pkg);
            p.endLine();
        }

        @Override
        public void visitRequire(final String module, final int access, final String version) {
            p.block("require");
            p.sym(module);
            accessRequire(access);
            p.val(version);
            p.endLine();
        }

        @Override
        public void visitExport(final String pkg, final int access, final String... modules) {
            p.block("export");
            p.sym(pkg);
            accessExport(access);
            p.syms(modules);
            p.endLine();
        }

        @Override
        public void visitOpen(final String pkg, final int access, final String... modules) {
            p.block("open");
            p.sym(pkg);
            accessExport(access);
            p.syms(modules);
            p.endLine();
        }

        @Override
        public void visitUse(final String service) {
            p.block("use");
            p.sym(service);
            p.endLine();
        }

        @Override
        public void visitProvide(final String service, final String... providers) {
            p.block("provide");
            p.sym(service);
            p.syms(providers);
            p.endLine();
        }

        @Override
        public void visitEnd() {
            p.unindent();
            p.endLine();
        }
    }

    private final class AnnotationPrinter extends AnnotationVisitor {
        private final boolean useName;

        AnnotationPrinter(final boolean un) {
            super(Pipeline.API);
            useName = un;
        }

        @Override
        public void visit(final String name, final Object value) {
            p.block("value");
            if (useName)
                p.sym(name);
            generic(value);
            p.endLine();
        }

        @Override
        public void visitEnum(final String name, final String descriptor, final String value) {
            p.block("enum");
            if (useName)
                p.sym(name);
            p.val(descriptor);
            p.sym(value);
            p.endLine();
        }

        @Override
        public AnnotationVisitor visitAnnotation(final String name, final String descriptor) {
            p.block("annotation");
            if (useName)
                p.sym(name);
            p.val(descriptor);
            p.indent();
            return annotationPrinter;
        }

        @Override
        public AnnotationVisitor visitArray(final String name) {
            p.block("array");
            if (useName)
                p.sym(name);
            p.indent();
            return namelessAnnotationPrinter;
        }

        @Override
        public void visitEnd() {
            p.unindent();
            p.endLine();
        }
    };

    private final class MethodPrinter extends MethodVisitor {
        private final HashMap<Label,String> labels = new HashMap<>();
        private boolean hasCode = false;

        private void label(final Label label) {
            String name = labels.get(label);
            if (name == null) {
                name = "L" + labels.size();
                labels.put(label, name);
            }
            p.sym(name);
        }

        private void labels(final Label[] lbls) {
            p.begin();
            for (Label l : lbls)
                label(l);
            p.end();
        }

        MethodPrinter() {
            super(Pipeline.API);
        }

        @Override
        public void visitEnd() {
            if (hasCode) {
                p.unindent();
                p.endLine();
            }
            p.unindent();
            p.endLine();
        }

        @Override
        public void visitParameter(final String name, final int access) {
            p.block("param");
            p.sym(name);
            accessParam(access);
            p.endLine();
        }

        @Override
        public AnnotationVisitor visitAnnotationDefault() {
            p.block("default");
            p.indent();
            return namelessAnnotationPrinter;
        }

        @Override
        public AnnotationVisitor visitAnnotation(final String descriptor, final boolean visible) {
            return ClassPrinter.this.visitAnnotation(descriptor, visible);
        }

        @Override
        public AnnotationVisitor visitTypeAnnotation(
            final int typeRef, final TypePath typePath, final String descriptor, final boolean visible) {
            return ClassPrinter.this.visitTypeAnnotation(typeRef, typePath, descriptor, visible);
        }

        @Override
        public void visitAnnotableParameterCount(final int parameterCount, final boolean visible) {
            p.block("annotable-param-count");
            p.val(parameterCount);
            p.val(visible);
            p.endLine();
        }

        @Override
        public AnnotationVisitor visitParameterAnnotation(final int parameter, final String descriptor, final boolean visible) {
            p.block("param-annotation");
            p.val(parameter);
            p.val(descriptor);
            p.val(visible);
            p.indent();
            return annotationPrinter;
        }

        @Override
        public void visitAttribute(final Attribute attribute) {
            ClassPrinter.this.visitAttribute(attribute);
        }

        @Override
        public void visitCode() {
            hasCode = true;
            p.block("code");
            p.indent();
        }

        private String frameType(final int type) {
            switch (type) {
            case F_NEW: return "new";
            case F_FULL: return "full";
            case F_APPEND: return "append";
            case F_CHOP: return "chop";
            case F_SAME: return "same";
            case F_SAME1: return "same1";
            default: throw new IllegalArgumentException();
            }
        }

        private void frameItems(final int nitems, final Object[] items) {
            p.begin();
            for (int i = 0; i < nitems && items != null; ++i) {
                final Object item = items[i];
                if (item == null) p.sym("null");
                else if (item instanceof String) p.val((String)item);
                else if (item instanceof Label) label((Label)item);
                else if (item == TOP) p.sym("T");
                else if (item == INTEGER) p.sym("I");
                else if (item == FLOAT) p.sym("F");
                else if (item == DOUBLE) p.sym("D");
                else if (item == LONG) p.sym("J");
                else if (item == NULL) p.sym("N");
                else if (item == UNINITIALIZED_THIS) p.sym("U");
                else throw new IllegalArgumentException(item.getClass().getName());
            }
            p.end();
        }

        @Override
        public void visitFrame(
            final int type,
            final int nLocal,
            final Object[] local,
            final int nStack,
            final Object[] stack) {
            p.block("frame");
            p.sym(frameType(type));
            frameItems(nLocal, local);
            frameItems(nStack, stack);
            p.endLine();
        }

        @Override
        public void visitInsn(final int opcode) {
            p.block(OPCODES[opcode]);
            p.endLine();
        }

        @Override
        public void visitIntInsn(final int opcode, final int operand) {
            p.block(OPCODES[opcode]);
            if (opcode == NEWARRAY) {
                switch (operand) {
                case T_BOOLEAN: p.sym("Z"); break;
                case T_CHAR: p.sym("C"); break;
                case T_FLOAT: p.sym("F"); break;
                case T_DOUBLE: p.sym("D"); break;
                case T_BYTE: p.sym("B"); break;
                case T_SHORT: p.sym("S"); break;
                case T_INT: p.sym("I"); break;
                case T_LONG: p.sym("J"); break;
                default: throw new IllegalArgumentException();
                }
            } else {
                p.val(operand);
            }
            p.endLine();
        }

        @Override
        public void visitVarInsn(final int opcode, final int var) {
            p.block(OPCODES[opcode]);
            p.val(var);
            p.endLine();
        }

        @Override
        public void visitTypeInsn(final int opcode, final String type) {
            p.block(OPCODES[opcode]);
            if (opcode == NEW || opcode == ANEWARRAY)
                p.sym(type);
            else
                p.val(type);
            p.endLine();
        }

        @Override
        public void visitFieldInsn(final int opcode, final String owner, final String name, final String descriptor) {
            p.block(OPCODES[opcode]);
            p.sym(owner);
            p.sym(name);
            p.val(descriptor);
            p.endLine();
        }

        @Override
        public void visitMethodInsn(
            final int opcode,
            final String owner,
            final String name,
            final String descriptor,
            final boolean isInterface) {
            p.block(OPCODES[opcode]);
            p.sym(owner);
            p.sym(name);
            p.val(descriptor);
            p.endLine();
        }

        @Override
        public void visitInvokeDynamicInsn(
            final String name,
            final String descriptor,
            final Handle bootstrapMethodHandle,
            final Object... bootstrapMethodArguments) {
            p.block("invokedynamic");
            p.sym(name);
            p.val(descriptor);
            p.indent();
            handle(bootstrapMethodHandle);
            p.newLine();
            generics(bootstrapMethodArguments);
            p.unindent();
            p.endLine();
        }

        @Override
        public void visitJumpInsn(final int opcode, final Label label) {
            p.block(OPCODES[opcode]);
            label(label);
            p.endLine();
        }

        @Override
        public void visitLabel(final Label label) {
            p.block("label");
            label(label);
            p.endLine();
        }

        @Override
        public void visitLdcInsn(final Object value) {
            p.block("ldc");
            generic(value);
            p.endLine();
        }

        @Override
        public void visitIincInsn(final int var, final int increment) {
            p.block("iinc");
            p.val(var);
            p.val(increment);
            p.endLine();
        }

        @Override
        public void visitTableSwitchInsn(final int min, final int max, final Label dflt, final Label... lbls) {
            p.block("tableswitch");
            p.val(min);
            p.val(max);
            label(dflt);
            labels(lbls);
            p.endLine();
        }

        @Override
        public void visitLookupSwitchInsn(final Label dflt, final int[] keys, final Label[] lbls) {
            p.block("lookupswitch");
            label(dflt);
            p.begin();
            for (int i = 0; i < keys.length; ++i) {
                p.begin();
                p.val(keys[i]);
                label(lbls[i]);
                p.end();
            }
            p.end();
            p.endLine();
        }

        @Override
        public void visitMultiANewArrayInsn(final String descriptor, final int numDimensions) {
            p.block("multianewarray");
            p.val(descriptor);
            p.val(numDimensions);
            p.endLine();
        }

        @Override
        public AnnotationVisitor visitInsnAnnotation(
            final int typeRef, final TypePath typePath, final String descriptor, final boolean visible) {
            return typeAnnotation("insn-annotation", typeRef, typePath, descriptor, visible);
        }

        @Override
        public void visitTryCatchBlock(final Label start, final Label end, final Label handler, final String type) {
            p.block("try-catch");
            label(start);
            label(end);
            label(handler);
            p.val(type);
            p.endLine();
        }

        @Override
        public AnnotationVisitor visitTryCatchAnnotation(final int typeRef, final TypePath typePath, final String descriptor, final boolean visible) {
            return typeAnnotation("try-catch-annotation", typeRef, typePath, descriptor, visible);
        }

        @Override
        public void visitLocalVariable(
            final String name,
            final String descriptor,
            final String signature,
            final Label start,
            final Label end,
            final int index) {
            p.block("local");
            p.sym(name);
            p.val(descriptor);
            p.val(signature);
            label(start);
            label(end);
            p.val(index);
            p.endLine();
        }

        @Override
        public AnnotationVisitor visitLocalVariableAnnotation(
            final int typeRef,
            final TypePath typePath,
            final Label[] start,
            final Label[] end,
            final int[] index,
            final String descriptor,
            final boolean visible) {

            p.block("local-annotation");
            p.val(typeRef);
            p.val(typePath.toString());

            labels(start);
            labels(end);

            p.begin();
            for (int i : index)
                p.val(i);
            p.end();

            p.val(descriptor);
            p.val(visible);
            p.indent();

            return annotationPrinter;
        }

        @Override
        public void visitLineNumber(final int line, final Label start) {
            p.block("line");
            p.val(line);
            label(start);
            p.endLine();
        }

        @Override
        public void visitMaxs(final int maxStack, final int maxLocals) {
            p.block("maxs");
            p.val(maxStack);
            p.val(maxLocals);
            p.endLine();
        }
    }

    private final class FieldPrinter extends FieldVisitor {
        FieldPrinter() {
            super(Pipeline.API);
        }

        @Override
        public AnnotationVisitor visitAnnotation(final String descriptor, final boolean visible) {
            return ClassPrinter.this.visitAnnotation(descriptor, visible);
        }

        @Override
        public AnnotationVisitor visitTypeAnnotation(final int typeRef, final TypePath typePath, final String descriptor, final boolean visible) {
            return ClassPrinter.this.visitTypeAnnotation(typeRef, typePath, descriptor, visible);
        }

        @Override
        public void visitAttribute(final Attribute attribute) {
            ClassPrinter.this.visitAttribute(attribute);
        }

        @Override
        public void visitEnd() {
            p.unindent();
            p.endLine();
        }
    }

    private final Writer      w;
    private final SExpPrinter p;
    private final AnnotationPrinter annotationPrinter = new AnnotationPrinter(true);
    private final AnnotationPrinter namelessAnnotationPrinter = new AnnotationPrinter(false);
    private final FieldPrinter      fieldPrinter = new FieldPrinter();
    private final ModulePrinter     modulePrinter = new ModulePrinter();

    public ClassPrinter(final Writer writer) {
        super(Pipeline.API, null);
        w = writer;
        p = new SExpPrinter(writer);
    }

    @Override
    public void visit(
        final int version,
        final int access,
        final String name,
        final String signature,
        final String superName,
        final String[] interfaces) {
        p.block("class");
        p.val(version);
        accessClass(access);
        p.sym(name);
        p.val(signature);
        p.sym(superName);
        p.syms(interfaces);
        p.indent();
    }

    @Override
    public void visitSource(final String file, final String debug) {
        p.block("source");
        p.val(file);
        p.val(debug);
        p.endLine();
    }

    @Override
    public ModuleVisitor visitModule(final String name, final int flags, final String version) {
        p.block("module");
        p.sym(name);
        moduleFlags(flags);
        p.val(version);
        p.indent();
        return modulePrinter;
    }

    @Override
    public void visitOuterClass(final String owner, final String name, final String descriptor) {
        p.block("outer-class");
        p.val(owner);
        p.sym(name);
        p.val(descriptor);
        p.endLine();
    }

    @Override
    public AnnotationVisitor visitAnnotation(final String descriptor, final boolean visible) {
        p.block("annotation");
        p.val(descriptor);
        p.val(visible);
        p.indent();
        return annotationPrinter;
    }

    private AnnotationVisitor typeAnnotation(final String block,
        final int typeRef, final TypePath typePath, final String descriptor, final boolean visible) {
        p.block(block);
        p.val(typeRef);
        p.val(typePath.toString());
        p.val(descriptor);
        p.val(visible);
        p.indent();
        return annotationPrinter;
    }

    @Override
    public AnnotationVisitor visitTypeAnnotation(
        final int typeRef, final TypePath typePath, final String descriptor, final boolean visible) {
        return typeAnnotation("type-annotation", typeRef, typePath, descriptor, visible);
    }

    @Override
    public void visitAttribute(final Attribute attribute) {
        p.block("attribute");
        // TODO
        p.endLine();
    }

    @Override
    public void visitInnerClass(final String name, final String outerName, final String innerName, final int access) {
        p.block("inner-class");
        p.sym(name);
        p.sym(outerName);
        p.sym(innerName);
        accessInner(access);
        p.endLine();
    }

    @Override
    public FieldVisitor visitField(
        final int access,
        final String name,
        final String descriptor,
        final String signature,
        final Object value) {
        p.block("field");
        accessField(access);
        p.sym(name);
        p.val(descriptor);
        p.val(signature);
        generic(value);
        p.indent();
        return fieldPrinter;
    }

    @Override
    public MethodVisitor visitMethod(
        final int access,
        final String name,
        final String descriptor,
        final String signature,
        final String[] exceptions) {
        p.block("method");
        accessMethod(access);
        p.sym(name);
        p.val(descriptor);
        p.val(signature);
        p.syms(exceptions);
        p.indent();
        return new MethodPrinter();
    }

    @Override
    public void visitEnd() {
        p.unindent();
        p.endLine();
        p.flush();
    }

    private void accessClass(final int a) {
        p.begin();
        if ((a & ACC_ABSTRACT) != 0) p.sym("abstract");
        if ((a & ACC_ANNOTATION) != 0) p.sym("annotation");
        if ((a & ACC_DEPRECATED) != 0) p.sym("deprecated");
        if ((a & ACC_ENUM) != 0) p.sym("enum");
        if ((a & ACC_FINAL) != 0) p.sym("final");
        if ((a & ACC_INTERFACE) != 0) p.sym("interface");
        if ((a & ACC_MODULE) != 0) p.sym("module");
        if ((a & ACC_PUBLIC) != 0) p.sym("public");
        if ((a & ACC_SUPER) != 0) p.sym("super");
        if ((a & ACC_SYNTHETIC) != 0) p.sym("synthetic");
        p.end();
    }

    private void accessField(final int a) {
        p.begin();
        if ((a & ACC_DEPRECATED) != 0) p.sym("deprecated");
        if ((a & ACC_ENUM) != 0) p.sym("enum");
        if ((a & ACC_FINAL) != 0) p.sym("final");
        if ((a & ACC_PRIVATE) != 0) p.sym("private");
        if ((a & ACC_PROTECTED) != 0) p.sym("protected");
        if ((a & ACC_PUBLIC) != 0) p.sym("public");
        if ((a & ACC_STATIC) != 0) p.sym("static");
        if ((a & ACC_SYNTHETIC) != 0) p.sym("synthetic");
        if ((a & ACC_TRANSIENT) != 0) p.sym("transient");
        if ((a & ACC_VOLATILE) != 0) p.sym("volatile");
        p.end();
    }

    private void accessMethod(final int a) {
        p.begin();
        if ((a & ACC_ABSTRACT) != 0) p.sym("abstract");
        if ((a & ACC_BRIDGE) != 0) p.sym("bridge");
        if ((a & ACC_DEPRECATED) != 0) p.sym("deprecated");
        if ((a & ACC_FINAL) != 0) p.sym("final");
        if ((a & ACC_NATIVE) != 0) p.sym("native");
        if ((a & ACC_PRIVATE) != 0) p.sym("private");
        if ((a & ACC_PROTECTED) != 0) p.sym("protected");
        if ((a & ACC_PUBLIC) != 0) p.sym("public");
        if ((a & ACC_STATIC) != 0) p.sym("static");
        if ((a & ACC_STRICT) != 0) p.sym("strictfp");
        if ((a & ACC_SYNCHRONIZED) != 0) p.sym("synchronized");
        if ((a & ACC_SYNTHETIC) != 0) p.sym("synthetic");
        if ((a & ACC_VARARGS) != 0) p.sym("varargs");
        p.end();
    }

    private void accessInner(final int a) {
        p.begin();
        if ((a & ACC_ABSTRACT) != 0) p.sym("abstract");
        if ((a & ACC_ANNOTATION) != 0) p.sym("annotation");
        if ((a & ACC_DEPRECATED) != 0) p.sym("deprecated");
        if ((a & ACC_ENUM) != 0) p.sym("enum");
        if ((a & ACC_FINAL) != 0) p.sym("final");
        if ((a & ACC_INTERFACE) != 0) p.sym("interface");
        if ((a & ACC_PRIVATE) != 0) p.sym("private");
        if ((a & ACC_PROTECTED) != 0) p.sym("protected");
        if ((a & ACC_PUBLIC) != 0) p.sym("public");
        if ((a & ACC_STATIC) != 0) p.sym("static");
        if ((a & ACC_SYNTHETIC) != 0) p.sym("synthetic");
        p.end();
    }

    private void accessParam(final int a) {
        p.begin();
        if ((a & ACC_FINAL) != 0) p.sym("final");
        if ((a & ACC_MANDATED) != 0) p.sym("mandated");
        if ((a & ACC_SYNTHETIC) != 0) p.sym("synthetic");
        p.end();
    }

    private void moduleFlags(final int a) {
        p.begin();
        if ((a & ACC_MANDATED) != 0) p.sym("mandated");
        if ((a & ACC_OPEN) != 0) p.sym("open");
        if ((a & ACC_SYNTHETIC) != 0) p.sym("synthetic");
        p.end();
    }

    private void accessRequire(final int a) {
        p.begin();
        if ((a & ACC_TRANSITIVE) != 0) p.sym("transitive");
        if ((a & ACC_MANDATED) != 0) p.sym("mandated");
        if ((a & ACC_SYNTHETIC) != 0) p.sym("synthetic");
        if ((a & ACC_STATIC_PHASE) != 0) p.sym("static_phase");
        p.end();
    }

    private void accessExport(final int a) {
        p.begin();
        if ((a & ACC_MANDATED) != 0) p.sym("mandated");
        if ((a & ACC_SYNTHETIC) != 0) p.sym("synthetic");
        p.end();
    }

    private String handleTag(final int tag) {
        switch (tag) {
        case H_GETFIELD: return "getfield";
        case H_GETSTATIC: return "getstatic";
        case H_PUTFIELD: return "putfield";
        case H_PUTSTATIC: return "putstatic";
        case H_INVOKEINTERFACE: return "invokeinterface";
        case H_INVOKESPECIAL: return "invokespecial";
        case H_INVOKESTATIC:return "invokestatic";
        case H_INVOKEVIRTUAL: return "invokevirtual";
        case H_NEWINVOKESPECIAL: return "newinvokespecial";
        default: throw new IllegalArgumentException();
        }
    }

    private void handle(final Handle h) {
        p.block("H");
        p.sym(handleTag(h.getTag()));
        p.sym(h.getOwner());
        p.sym(h.getName());
        p.indent();
        p.val(h.getDesc());
        p.val(h.isInterface());
        p.unindent();
        p.end();
    }

    private void generic(final Object v) {
        if (v == null) {
            p.sym("null");
        } else if (v instanceof String) {
            p.val((String)v);
        } else if (v instanceof Boolean) {
            p.block("Z");
            p.val((boolean)v);
            p.end();
        } else if (v instanceof Character) {
            p.block("C");
            p.val((char)v);
            p.end();
        } else if (v instanceof Byte) {
            p.block("B");
            p.val((byte)v);
            p.end();
        } else if (v instanceof Short) {
            p.block("S");
            p.val((short)v);
            p.end();
        } else if (v instanceof Integer) {
            p.block("I");
            p.val((int)v);
            p.end();
        } else if (v instanceof Long) {
            p.block("J");
            p.val((long)v);
            p.end();
        } else if (v instanceof Float) {
            p.block("F");
            p.val((float)v);
            p.end();
        } else if (v instanceof Double) {
            p.block("D");
            p.val((double)v);
            p.end();
        } else if (v instanceof Type) {
            p.block("T");
            p.val(((Type)v).getDescriptor());
            p.end();
        } else if (v instanceof Handle) {
            handle((Handle)v);
        } else if (v instanceof int[]) {
            // TODO add more primitive array types if needed
            p.block("[I");
            for (int x : (int[])v)
                p.val(x);
            p.end();
        } else {
            throw new IllegalArgumentException("Invalid value " + v + " of type " + v.getClass().getName(), null);
        }
    }

    private void generics(final Object[] v) {
        if (v == null) {
            p.sym("null");
        } else {
            p.begin();
            p.indent();
            for (final Object s : v) {
                generic(s);
                p.newLine();
            }
            p.unindent();
            p.end();
        }
    }

    @Override
    public void close() throws IOException {
        w.close();
    }

    @Override
    public ClassVisitor write() {
        return this;
    }
}
