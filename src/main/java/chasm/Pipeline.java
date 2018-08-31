package chasm;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.FileTime;
import java.util.LinkedList;
import java.util.TreeMap;
import java.util.TreeSet;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.util.CheckClassAdapter;

public final class Pipeline {
    public static final int FRAMES        = 1;
    public static final int MAXS          = 2;
    public static final int VERIFY_ASM    = 4;
    public static final int VERIFY_NATIVE = 8;
    private static final int VERIFY       = VERIFY_ASM | VERIFY_NATIVE;
    private static final FileTime EPOCH = FileTime.fromMillis(315532800000L);

    public static final int API = Opcodes.ASM6;

    private final int flags;
    private final PrintStream log;

    public Pipeline(final int f, final PrintStream l) {
        flags = f;
        log = l;
    }

    public void process(final Path input, final Path output) throws IOException {
        run(selectInput(input), selectOutput(output));
    }

    private static void run(final ClassInput input, final ClassOutput output) throws IOException {
        try {
            boolean more;
            ClassVisitor v = output.write();
            do {
                more = input.read(v);
                v = output.write();
            } while (more && v != null);
        } finally {
            input.close();
            output.close();
        }
    }

    private static LinkedList<Path> getAllClassFiles(final FileSystem fs) throws IOException {
        final TreeSet<Path> paths = new TreeSet<>();
        Files.walkFileTree(fs.getPath("/"), new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(final Path p, final BasicFileAttributes attrs) {
                    if (p.toString().endsWith(".class"))
                        paths.add(p);
                    return FileVisitResult.CONTINUE;
                }
            });
        return new LinkedList<>(paths);
    }

    private static void createParentDir(final Path path) throws IOException {
        final Path parent = path.getParent();
        if (parent != null && !Files.exists(parent)) {
            createParentDir(parent);
            Files.createDirectory(parent);
            Files.getFileAttributeView(parent, BasicFileAttributeView.class).setTimes(EPOCH, EPOCH, EPOCH);
        }
    }

    private ClassInput jarInput(final Path input) throws IOException {
        final FileSystem fs = FileSystems.newFileSystem(URI.create("jar:file:" + input.toAbsolutePath()), new TreeMap<>());
        final LinkedList<Path> paths = getAllClassFiles(fs);
        return new ClassInput() {
            @Override
            public void close() throws IOException { fs.close(); }

            @Override
            public boolean read(final ClassVisitor v) throws IOException {
                if (paths.isEmpty())
                    throw new IOException("No class found in jar file");
                bytecodeInput(paths.pop()).read(v);
                return !paths.isEmpty();
            }
        };
    }

    private ClassOutput jarOutput(final Path output) throws IOException {
        final TreeMap<String, String> env = new TreeMap<>();
        env.put("create", "true");
        final FileSystem fs = FileSystems.newFileSystem(URI.create("jar:file:" + output.toAbsolutePath()), env);

        class JarOutput extends ClassVisitor implements ClassOutput {
            private ClassOutput out;
            private Path path;

            JarOutput() {
                super(API);
            }

            @Override
            public void close() throws IOException { fs.close(); }

            @Override
            public ClassVisitor write() {
                return this;
            }

            @Override
            public void visit(final int version,
                              final int access,
                              final String name,
                              final String signature,
                              final String superName,
                              final String[] interfaces) {
                try {
                    path = fs.getPath(name + ".class");
                    createParentDir(path);
                    out = bytecodeOutput(path);
                    cv = out.write();
                    cv.visit(version, access, name, signature, superName, interfaces);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }

            @Override
            public void visitEnd() {
                try {
                    cv = null;
                    out.close();
                    Files.getFileAttributeView(path, BasicFileAttributeView.class).setTimes(EPOCH, EPOCH, EPOCH);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }

        return new JarOutput();
    }

    private static final ClassOutput DUMMY_OUTPUT = new ClassOutput() {
            @Override
            public void close() {}

            @Override
            public ClassVisitor write() { return new ClassNode(); }
        };

    private ClassOutput bytecodeOutput(final Path output) throws IOException {
        int opt = 0;
        if ((flags & FRAMES) != 0)
            opt = ClassWriter.COMPUTE_FRAMES;
        else if ((flags & MAXS) != 0)
            opt = ClassWriter.COMPUTE_MAXS;

        final ClassWriter classWriter = new ClassWriter(opt);
        final ClassNode classNode = opt != 0 ? new ClassNode() : null;

        return new ClassOutput() {
            private ClassVisitor classVisitor = classNode != null ? classNode : classWriter;

            @Override
            public void close() throws IOException {
                if (classNode != null) {
                    DebugVisitor debugVisitor = new DebugVisitor(classWriter);
                    try {
                        classNode.accept(debugVisitor);
                    } catch (Exception e) {
                        throw new IOException("ClassWriter crashed while processing method "
                                              + debugVisitor.getLastMethod() + ".\nInvalid bytecode?", e);
                    }
                }
                final byte[] data = classWriter.toByteArray();
                if ((flags & VERIFY) != 0)
                    verifyClass(data);
                Files.write(output, data);
            }

            @Override
            public ClassVisitor write() {
                ClassVisitor v = classVisitor;
                classVisitor = null;
                return v;
            }
        };
    }

    private void verifyClass(final byte[] data) {
        if ((flags & VERIFY_NATIVE) != 0)
            verifyClassNative(data);
        if ((flags & VERIFY_ASM) != 0)
            verifyClassAsm(data);
    }

    private void verifyClassAsm(final byte[] data) {
        CheckClassAdapter.verify(new ClassReader(data), null, true, new PrintWriter(new OutputStreamWriter(log)));
    }

    private static void verifyClassNative(final byte[] data) {
        new ByteClassLoader().loadClass(data);
    }

    private ClassInput selectInput(final Path input) throws IOException {
        final String s = input.toString();
        if (s.endsWith(".class"))
            return bytecodeInput(input);
        if (s.endsWith(".jar"))
            return jarInput(input);
        return new ClassParser(Files.newBufferedReader(input, StandardCharsets.UTF_8));
    }

    private ClassOutput selectOutput(final Path output) throws IOException {
        if (output == null)
            return DUMMY_OUTPUT;
        final String s = output.toString();
        if (s.endsWith(".class"))
            return bytecodeOutput(output);
        if (s.endsWith(".jar"))
            return jarOutput(output);
        return new ClassPrinter(Files.newBufferedWriter(output, StandardCharsets.UTF_8));
    }

    private ClassInput bytecodeInput(final Path input) throws IOException {
        final byte[] data = Files.readAllBytes(input);
        if ((flags & VERIFY) != 0)
            verifyClass(data);
        return new ClassInput() {
            @Override
            public void close() {}

            @Override
            public boolean read(final ClassVisitor v) {
                new ClassReader(data).accept(v, 0);
                return false;
            }
        };
    }
}
