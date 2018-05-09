package chasm;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;

public final class DebugVisitor extends ClassVisitor {
    private String lastMethod;

    public DebugVisitor(final ClassVisitor v) {
        super(Pipeline.API, v);
    }

    public String getLastMethod() {
        return lastMethod;
    }

    @Override
    public MethodVisitor visitMethod(
        final int access,
        final String name,
        final String descriptor,
        final String signature,
        final String[] exceptions) {
        lastMethod = name;
        return super.visitMethod(access, name, descriptor, signature, exceptions);
    }

    @Override
    public void visitEnd() {
        lastMethod = null;
    }
}
