package chasm;

import java.io.IOException;
import java.io.Reader;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.Attribute;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;
import org.objectweb.asm.TypePath;
import org.objectweb.asm.util.Printer;
import static org.objectweb.asm.Opcodes.*;

public final class ClassParser implements ClassInput {
    private static final HashMap<String, Integer> OPCODES = new HashMap<>();
    static {
        for (int i = 0; i < Printer.OPCODES.length; ++i)
            OPCODES.put(Printer.OPCODES[i].toLowerCase(), i);
    }

    private static final HashSet<Integer> INSN = hashSet(
                NOP, ACONST_NULL, ICONST_M1, ICONST_0, ICONST_1, ICONST_2, ICONST_3, ICONST_4, ICONST_5,
                LCONST_0, LCONST_1, FCONST_0, FCONST_1, FCONST_2,
                DCONST_0, DCONST_1, IALOAD, LALOAD, FALOAD, DALOAD, AALOAD, BALOAD, CALOAD, SALOAD,
                IASTORE, LASTORE, FASTORE, DASTORE, AASTORE, BASTORE, CASTORE, SASTORE,
                POP, POP2, DUP, DUP_X1, DUP_X2, DUP2, DUP2_X1, DUP2_X2,
                SWAP, IADD, LADD, FADD, DADD, ISUB, LSUB, FSUB, DSUB, IMUL, LMUL, FMUL, DMUL,
                IDIV, LDIV, FDIV, DDIV, IREM, LREM, FREM, DREM, INEG, LNEG, FNEG, DNEG,
                ISHL, LSHL, ISHR, LSHR, IUSHR, LUSHR, IAND, LAND, IOR, LOR, IXOR, LXOR,
                I2L, I2F, I2D, L2I, L2F, L2D, F2I, F2L, F2D, D2I, D2L, D2F, I2B, I2C,
                I2S, LCMP, FCMPL, FCMPG, DCMPL, DCMPG,
                IRETURN, LRETURN, FRETURN, DRETURN, ARETURN, RETURN, ARRAYLENGTH, ATHROW,
                MONITORENTER, MONITOREXIT),
        VAR_INSN = hashSet(ILOAD, LLOAD, FLOAD, DLOAD, ALOAD, ISTORE, LSTORE, FSTORE, DSTORE, ASTORE, RET),
        METHOD_INSN = hashSet(INVOKEVIRTUAL, INVOKESPECIAL, INVOKESTATIC, INVOKEINTERFACE),
        INT_INSN = hashSet(BIPUSH, SIPUSH, NEWARRAY),
        JUMP_INSN = hashSet(IFEQ, IFNE, IFLT, IFGE, IFGT, IFLE, IF_ICMPEQ, IF_ICMPNE, IF_ICMPLT, IF_ICMPGE,
                           IF_ICMPGT, IF_ICMPLE, IF_ACMPEQ, IF_ACMPNE, GOTO, JSR, IFNULL, IFNONNULL),
        FIELD_INSN = hashSet(GETSTATIC, PUTSTATIC, GETFIELD, PUTFIELD);

    private static HashSet<Integer> hashSet(final Integer... i) {
        return new HashSet<Integer>(Arrays.asList(i));
    }

    private Reader r;
    private SExpParser p;

    public ClassParser(final Reader reader) {
        r = reader;
        p = new SExpParser(reader);
    }

    @Override
    public void close() throws IOException {
        r.close();
    }

    @Override
    public boolean read(final ClassVisitor v) {
        p.block("class");
        final int version = p.intVal();
        final int access = access();
        final String name = p.sym();
        final String signature = p.strVal();
        final String superName = p.sym();
        final String[] interfaces = p.syms();
        v.visit(version, access, name, signature, superName, interfaces);

        while (p.more()) {
            p.begin();
            final String sym = p.sym();
            switch (sym) {
            case "source":      parseSource(v);     break;
            case "inner-class": parseInnerClass(v); break;
            case "outer-class": parseOuterClass(v); break;
            case "module":      parseModule(v);     break;
            case "field":       parseField(v);      break;
            case "method":      parseMethod(v);     break;
            case "annotation":  parseClassAnnotation(v); break;
            default:            p.err("Unexpected symbol " + sym);
            }
            p.end();
        }

        p.end();
        v.visitEnd();
        return p.more();
    }

    private void parseSource(final ClassVisitor v) {
        final String file = p.strVal();
        final String debug = p.strVal();
        v.visitSource(file, debug);
    }

    private void parseInnerClass(final ClassVisitor v) {
        final String name = p.sym();
        final String outerName = p.sym();
        final String innerName = p.sym();
        final int access = access();
        v.visitInnerClass(name, outerName, innerName, access);
    }

    private void parseOuterClass(final ClassVisitor v) {
        final String owner = p.strVal();
        final String name = p.sym();
        final String descriptor = p.strVal();
        v.visitOuterClass(owner, name, descriptor);
    }

    private void parseModule(final ClassVisitor v) {
        final String name = p.sym();
        final int flags = access();
        final String version = p.strVal();
        v.visitModule(name, flags, version);
        // TODO Module parser
    }

    private void parseField(final ClassVisitor v) {
        final int access = access();
        final String name = p.sym();
        final String descriptor = p.strVal();
        final String signature = p.strVal();
        final Object value = generic();
        final FieldVisitor f = v.visitField(access, name, descriptor, signature, value);
        while (p.more()) {
            p.begin();
            final String sym = p.sym();
            switch (sym) {
            case "annotation": parseFieldAnnotation(f); break;
            case "type-annotation": parseFieldTypeAnnotation(f); break;
            case "attribute": f.visitAttribute(parseAttribute()); break;
            default: p.err("Unexpected symbol " + sym); break;
            }
            p.end();
        }
        f.visitEnd();
    }

    private void parseClassAnnotation(final ClassVisitor v) {
        final String descriptor = p.strVal();
        final boolean visible = p.boolVal();
        parseAnnotation(true, v.visitAnnotation(descriptor, visible));
    }

    private void parseFieldAnnotation(final FieldVisitor v) {
        final String descriptor = p.strVal();
        final boolean visible = p.boolVal();
        parseAnnotation(true, v.visitAnnotation(descriptor, visible));
    }

    private void parseFieldTypeAnnotation(final FieldVisitor v) {
        final int typeRef = p.intVal();
        final TypePath typePath = TypePath.fromString(p.strVal());
        final String descriptor = p.strVal();
        final boolean visible = p.boolVal();
        parseAnnotation(true, v.visitTypeAnnotation(typeRef, typePath, descriptor, visible));
    }

    private void parseAnnotation(final boolean useName, final AnnotationVisitor v) {
        while (p.more()) {
            p.begin();
            final String sym = p.sym();
            final String name = useName ? p.sym() : null;
            switch (sym) {
            case "value": parseAnnotationValue(name, v); break;
            case "enum": parseAnnotationEnum(name, v); break;
            case "annotation": parseAnnotationAnnotation(name, v); break;
            case "array": parseAnnotationArray(name, v); break;
            default: p.err("Unexpected symbol " + sym); break;
            }
            p.end();
        }
        v.visitEnd();
    }

    private void parseAnnotationValue(final String name, final AnnotationVisitor v) {
        final Object value = generic();
        v.visit(name, value);
    }

    private void parseAnnotationEnum(final String name, final AnnotationVisitor v) {
        final String descriptor = p.strVal();
        final String value = p.sym();
        v.visitEnum(name, descriptor, value);
    }

    private void parseAnnotationAnnotation(final String name, final AnnotationVisitor v) {
        final String descriptor = p.strVal();
        parseAnnotation(true, v.visitAnnotation(name, descriptor));
    }

    public void parseAnnotationArray(final String name, final AnnotationVisitor v) {
        parseAnnotation(false, v.visitArray(name));
    }

    private Attribute parseAttribute() {
        // TODO
        return null;
    }

    private void parseMethod(final ClassVisitor v) {
        final int access = access();
        final String name = p.sym();
        final String descriptor = p.strVal();
        final String signature = p.strVal();
        final String[] exceptions = p.syms();
        final MethodVisitor m = v.visitMethod(access, name, descriptor, signature, exceptions);

        while (p.more()) {
            p.begin();
            final String sym = p.sym();
            switch (sym) {
            case "param": parseMethodParam(m); break;
            case "default": parseAnnotation(false, m.visitAnnotationDefault()); break;
            case "annotation": parseMethodAnnotation(m); break;
            case "type-annotation": parseMethodTypeAnnotation(m); break;
            case "annotable-param-count": parseMethodAnnotableParamCount(m); break;
            case "param-annotation": parseMethodParamAnnotation(m); break;
            case "attribute": m.visitAttribute(parseAttribute()); break;
            case "code": parseCode(m); break;
            default: p.err("Unexpected symbol " + sym); break;
            }
            p.end();
        }
        m.visitEnd();
    }

    private void parseMethodTypeAnnotation(final MethodVisitor v) {
        final int typeRef = p.intVal();
        final TypePath typePath = TypePath.fromString(p.strVal());
        final String descriptor = p.strVal();
        final boolean visible = p.boolVal();
        parseAnnotation(true, v.visitTypeAnnotation(typeRef, typePath, descriptor, visible));
    }

    private void parseMethodParamAnnotation(final MethodVisitor v) {
        final int parameter = p.intVal();
        final String descriptor = p.strVal();
        final boolean visible = p.boolVal();
        parseAnnotation(true, v.visitParameterAnnotation(parameter, descriptor, visible));
    }

    private void parseMethodAnnotableParamCount(final MethodVisitor v) {
        final int parameterCount = p.intVal();
        final boolean visible = p.boolVal();
        v.visitAnnotableParameterCount(parameterCount, visible);
    }

    private void parseMethodParam(final MethodVisitor v) {
        final String name = p.sym();
        int access = access();
        v.visitParameter(name, access);
    }

    private void parseMethodAnnotation(final MethodVisitor v) {
        final String descriptor = p.strVal();
        final boolean visible = p.boolVal();
        parseAnnotation(true, v.visitAnnotation(descriptor, visible));
    }

    private void parseCode(final MethodVisitor v) {
        v.visitCode();
        final HashMap<String, Label> labels = new HashMap<>();
        while (p.more()) {
            p.begin();
            final String sym = p.sym();
            switch (sym) {
            case "label": v.visitLabel(label(labels)); break;
            case "maxs": parseCodeMaxs(v); break;
            case "line": parseCodeLine(v, labels); break;
            case "try-catch": parseCodeTryCatchBlock(v, labels); break;
            case "local": parseCodeLocalVariable(v, labels); break;
            case "insn-annotation": parseMethodTypeAnnotation(v); break;
            case "try-catch-annotation": parseMethodTypeAnnotation(v); break;
            case "local-annotation": parseCodeLocalVariableAnnotation(v, labels); break;
            case "frame": parseCodeFrame(v, labels); break;
            default: parseCodeInsn(sym, v, labels); break;
            }
            p.end();
        }
    }

    private void parseCodeLocalVariableAnnotation(final MethodVisitor v, final HashMap<String, Label> labels) {
        final int typeRef = p.intVal();
        final TypePath typePath = TypePath.fromString(p.strVal());
        final Label[] start = labels(labels);
        final Label[] end = labels(labels);

        final IntStream.Builder index = IntStream.builder();
        p.begin();
        while (p.more())
            index.add(p.intVal());
        p.end();

        final String descriptor = p.strVal();
        final boolean visible = p.boolVal();
        parseAnnotation(true, v.visitLocalVariableAnnotation(typeRef, typePath, start, end, index.build().toArray(), descriptor, visible));
    }

    private Object frameItem(final HashMap<String, Label> labels) {
        if (p.isStrVal())
            return p.strVal();
        final String sym = p.sym();
        switch (sym) {
        case "T": return TOP;
        case "I": return INTEGER;
        case "F": return FLOAT;
        case "D": return DOUBLE;
        case "J": return LONG;
        case "N": return NULL;
        case "U": return UNINITIALIZED_THIS;
        default:  return sym2label(labels, sym);
        }
    }

    private Object[] frameItems(final HashMap<String, Label> labels) {
        p.begin();
        final Stream.Builder<Object> items = Stream.builder();
        while (p.more())
            items.add(frameItem(labels));
        p.end();
        return items.build().toArray();
    }

    private void parseCodeFrame(final MethodVisitor v, final HashMap<String, Label> labels) {
        final int type = frameType();
        final Object[] local = frameItems(labels);
        final Object[] stack = frameItems(labels);
        v.visitFrame(type, local.length, local, stack.length, stack);
    }

    private int frameType() {
        switch (p.sym()) {
        case "new": return F_NEW;
        case "full": return F_FULL;
        case "append": return F_APPEND;
        case "chop": return F_CHOP;
        case "same": return F_SAME;
        case "same1": return F_SAME1;
        default: p.err("Invalid frame type"); return -1;
        }
    }

    private void parseCodeLocalVariable(final MethodVisitor v, final HashMap<String, Label> labels) {
        final String name = p.sym();
        final String descriptor = p.strVal();
        final String signature = p.strVal();
        final Label start = label(labels);
        final Label end = label(labels);
        final int index = p.intVal();
        v.visitLocalVariable(name, descriptor, signature, start, end, index);
    }

    private void parseCodeInsn(final String opName, final MethodVisitor v, final HashMap<String, Label> labels) {
        final int opcode = OPCODES.get(opName);
        switch (opcode) {
        case LDC:
        {
            final Object value = generic();
            v.visitLdcInsn(value);
            break;
        }
        case IINC:
        {
            final int var = p.intVal();
            final int increment = p.intVal();
            v.visitIincInsn(var, increment);
            break;
        }
        case MULTIANEWARRAY:
        {
            final String descriptor = p.strVal();
            final int numDimensions = p.intVal();
            v.visitMultiANewArrayInsn(descriptor, numDimensions);
            break;
        }
        case TABLESWITCH:
        {
            final int min = p.intVal();
            final int max = p.intVal();
            final Label dflt = label(labels);
            final Label[] table = labels(labels);
            v.visitTableSwitchInsn(min, max, dflt, table);
            break;
        }
        case LOOKUPSWITCH:
        {
            final Label dflt = label(labels);
            final IntStream.Builder tableKeys = IntStream.builder();
            final Stream.Builder<Label> tableLabels = Stream.builder();
            p.begin();
            while (p.more()) {
                p.begin();
                final int key = p.intVal();
                final Label label = label(labels);
                p.end();
                tableKeys.add(key);
                tableLabels.add(label);
            }
            p.end();
            v.visitLookupSwitchInsn(dflt, tableKeys.build().toArray(), tableLabels.build().toArray(Label[]::new));
            break;
        }
        case INVOKEDYNAMIC:
        {
            final String name = p.sym();
            final String descriptor = p.strVal();
            p.block("H");
            final Handle bootstrapMethodHandle = handle();
            p.end();
            final Object[] bootstrapMethodArguments = generics();
            v.visitInvokeDynamicInsn(name, descriptor, bootstrapMethodHandle, bootstrapMethodArguments);
            break;
        }
        case NEWARRAY:
        {
            int type = 0;
            switch (p.sym()) {
            case "Z": type = T_BOOLEAN; break;
            case "C": type = T_CHAR; break;
            case "F": type = T_FLOAT; break;
            case "D": type = T_DOUBLE; break;
            case "B": type = T_BYTE; break;
            case "S": type = T_SHORT; break;
            case "I": type = T_INT; break;
            case "J": type = T_LONG; break;
            default: p.err("Invalid array type"); break;
            }
            v.visitIntInsn(NEWARRAY, type);
            break;
        }
        case NEW:
        case ANEWARRAY:
        {
            final String type = p.sym();
            v.visitTypeInsn(opcode, type);
            break;
        }
        case CHECKCAST:
        case INSTANCEOF:
        {
            final String type = p.strVal();
            v.visitTypeInsn(opcode, type);
            break;
        }
        default:
            if (INSN.contains(opcode)) {
                v.visitInsn(opcode);
            } else if (VAR_INSN.contains(opcode)) {
                final int var = p.intVal();
                v.visitVarInsn(opcode, var);
            } else if (INT_INSN.contains(opcode)) {
                final int val = p.intVal();
                v.visitIntInsn(opcode, val);
            } else if (JUMP_INSN.contains(opcode)) {
                final Label label = label(labels);
                v.visitJumpInsn(opcode, label);
            } else if (FIELD_INSN.contains(opcode)) {
                final String owner = p.sym();
                final String name = p.sym();
                final String descriptor = p.strVal();
                v.visitFieldInsn(opcode, owner, name, descriptor);
            } else if (METHOD_INSN.contains(opcode)) {
                final String owner = p.sym();
                final String name = p.sym();
                final String descriptor = p.strVal();
                final boolean isInterface = opcode == INVOKEINTERFACE || (opcode == INVOKESTATIC && p.isBoolVal() && p.boolVal());
                v.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
            } else {
                p.err("Unexpected instruction " + opName);
            }
        }
    }

    private void parseCodeTryCatchBlock(final MethodVisitor v, final HashMap<String, Label> labels) {
        final Label start = label(labels);
        final Label end = label(labels);
        final Label handler = label(labels);
        final String type = p.strVal();
        v.visitTryCatchBlock(start, end, handler, type);
    }

    private void parseCodeMaxs(final MethodVisitor v) {
        final int maxStack = p.intVal();
        final int maxLocals = p.intVal();
        v.visitMaxs(maxStack, maxLocals);
    }

    private void parseCodeLine(final MethodVisitor v, final HashMap<String, Label> labels) {
        final int line = p.intVal();
        final Label label = label(labels);
        v.visitLineNumber(line, label);
    }

    private int access() {
        int a = 0;
        p.begin();
        while (p.more()) {
            final String s = p.sym();
            switch (s) {
            case "abstract": a |= ACC_ABSTRACT; break;
            case "annotation": a |= ACC_ANNOTATION; break;
            case "bridge": a |= ACC_BRIDGE; break;
            case "deprecated": a |= ACC_DEPRECATED; break;
            case "enum": a |= ACC_ENUM; break;
            case "final": a |= ACC_FINAL; break;
            case "interface": a |= ACC_INTERFACE; break;
            case "mandated": a |= ACC_MANDATED; break;
            case "module": a |= ACC_MODULE; break;
            case "native": a |= ACC_NATIVE; break;
            case "open": a |= ACC_OPEN; break;
            case "private": a |= ACC_PRIVATE; break;
            case "protected": a |= ACC_PROTECTED; break;
            case "public": a |= ACC_PUBLIC; break;
            case "static": a |= ACC_STATIC; break;
            case "static_phase": a |= ACC_STATIC_PHASE; break;
            case "strictfp": a |= ACC_STRICT; break;
            case "super": a |= ACC_SUPER; break;
            case "synchronized": a |= ACC_SYNCHRONIZED; break;
            case "synthetic": a |= ACC_SYNTHETIC; break;
            case "transient": a |= ACC_TRANSIENT; break;
            case "transitive": a |= ACC_TRANSITIVE; break;
            case "varargs": a |= ACC_VARARGS; break;
            case "volatile": a |= ACC_VOLATILE; break;
            default: p.err("Invalid access token " + s);
            }
        }
        p.end();
        return a;
    }

    private Label label(final HashMap<String, Label> labels) {
        return sym2label(labels, p.sym());
    }

    private Label sym2label(final HashMap<String, Label> labels, final String name) {
        if (name.charAt(0) != 'L')
            p.err("Expected label, but got" + name);
        Label label = labels.get(name);
        if (label == null) {
            label = new Label();
            labels.put(name, label);
        }
        return label;
    }

    private int handleTag() {
        switch (p.sym()) {
        case "getfield": return H_GETFIELD;
        case "getstatic": return H_GETSTATIC;
        case "putfield": return H_PUTFIELD;
        case "putstatic": return H_PUTSTATIC;
        case "invokeinterface": return H_INVOKEINTERFACE;
        case "invokespecial": return H_INVOKESPECIAL;
        case "invokestatic": return H_INVOKESTATIC;
        case "invokevirtual": return H_INVOKEVIRTUAL;
        case "newinvokespecial": return H_NEWINVOKESPECIAL;
        default: p.err("Invalid tag"); return -1;
        }
    }

    private Handle handle() {
        final int tag = handleTag();
        final String owner = p.sym();
        final String name = p.sym();
        final String descriptor = p.strVal();
        final boolean isInterface = p.boolVal();
        return new Handle(tag, owner, name, descriptor, isInterface);
    }

    public Object generic() {
        if (p.isStrVal())
            return p.strVal();
        p.begin();
        final String type = p.sym();
        Object val = null;
        switch (type) {
        case "H": val = handle(); break;
        case "T": val = Type.getType(p.strVal()); break;
        case "B": val = p.byteVal(); break;
        case "F": val = p.floatVal(); break;
        case "D": val = p.doubleVal(); break;
        case "S": val = p.shortVal(); break;
        case "I": val = p.intVal(); break;
        case "J": val = p.longVal(); break;
        case "Z": val = p.boolVal(); break;
        case "C": val = p.charVal(); break;
        case "[I":
        {
            final IntStream.Builder vals = IntStream.builder();
            while (p.more())
                vals.add(p.intVal());
            val = vals.build().toArray();
            break;
        }
        default: p.err("Invalid value type " + type);
        }
        p.end();
        return val;
    }

    public Object[] generics() {
        if (p.isNull())
            return null;
        final Stream.Builder<Object> list = Stream.builder();
        p.begin();
        while (p.more())
            list.add(generic());
        p.end();
        return list.build().toArray();
    }

    public Label[] labels(final HashMap<String, Label> labels) {
        p.begin();
        final Stream.Builder<Label> table = Stream.builder();
        while (p.more())
            table.add(label(labels));
        p.end();
        return table.build().toArray(Label[]::new);
    }
}
