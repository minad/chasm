package chasm;

import java.io.Closeable;
import java.io.IOException;
import org.objectweb.asm.ClassVisitor;

public interface ClassInput extends Closeable {
    boolean read(ClassVisitor v) throws IOException;
}
