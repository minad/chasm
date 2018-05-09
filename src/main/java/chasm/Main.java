package chasm;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class Main {
    private Main() {
    }

    public static void main(final String[] args) throws IOException {
        Server.main(Main::run, args);
    }

    private static void run(final Path cwd, final String[] args, final PrintStream out) {
        int flags = 0, i = 0;
        while (i < args.length) {
            if (args[i].equals("-f"))
                flags |= Pipeline.FRAMES;
            else if (args[i].equals("-m"))
                flags |= Pipeline.MAXS;
            else if (args[i].equals("-va"))
                flags |= Pipeline.VERIFY_ASM;
            else if (args[i].equals("-vn"))
                flags |= Pipeline.VERIFY_NATIVE;
            else if (args[i].startsWith("-"))
                throw new IllegalArgumentException(args[i]);
            else
                break;
            ++i;
            }

        if (i + 1 != args.length && i + 2 != args.length)
            throw new IllegalArgumentException("Usage: chasm [-f|-m|-va|-vn] input [output]");

        try {
            new Pipeline(flags, out).process(cwd.resolve(Paths.get(args[i])),
                                             args.length == i + 2 ? cwd.resolve(Paths.get(args[i + 1])) : null);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
