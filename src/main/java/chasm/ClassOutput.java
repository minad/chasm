package chasm;

import java.io.Closeable;
import java.io.IOException;
import org.objectweb.asm.ClassVisitor;

public interface ClassOutput extends Closeable {
    ClassVisitor write() throws IOException;
}
