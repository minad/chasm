package chasm;

import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

public final class Server {
    private static final Pattern
        SPLITTER = Pattern.compile("([^ ]|\\\\ )+"),
        REPLACER = Pattern.compile("\\\\ ");

    public interface Command {
        void run(Path cwd, String[] args, PrintStream out);
    }

    private Server() {
    }

    static void main(final Command cmd, final String[] args) {
        if (args.length > 0 && args[0].equals("server")) {
            if (args.length != 3)
                throw new IllegalArgumentException("Server command requires two arguments: server portfile logfile");
            runServer(cmd, Paths.get(args[1]), args[2]);
        } else {
            cmd.run(Paths.get("").toAbsolutePath(), args, System.err);
        }
    }

    private static void handleClient(final Command cmd, final Socket client, final PrintStream log) {
        try {
            final BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream(), "UTF-8"));
            final PrintStream out = new PrintStream(new TeeOutputStream(log, client.getOutputStream()));
            try {
                final String line = in.readLine();
                if (line != null) {
                    log.println(line);
                    final String[] args = SPLITTER.matcher(line).results().map(r -> REPLACER.matcher(r.group()).replaceAll(" ")).toArray(String[]::new);
                    if (args.length == 0)
                        throw new IllegalArgumentException("Invalid command");
                    final Path cwd = Paths.get(args[0]);
                    if (!cwd.isAbsolute())
                        throw new IllegalArgumentException("Current directory is not absolute");
                    final String[] rest = new String[args.length - 1];
                    System.arraycopy(args, 1, rest, 0, rest.length);
                    cmd.run(cwd, rest, out);
                }
            } catch (Exception e) {
                e.printStackTrace(out);
            } finally {
                client.close();
            }
        } catch (IOException e) {
            e.printStackTrace(log);
        }
    }

    private static void runServer(final Command cmd, final Path portFile, final String logFile) {
        try {
            final PrintStream log = new PrintStream(new FileOutputStream(logFile, true));
            final ExecutorService pool = Executors.newCachedThreadPool();
            final ServerSocket socket = new ServerSocket(0);
            try {
                Files.write(portFile, Integer.toString(socket.getLocalPort()).getBytes());
                Runtime.getRuntime().addShutdownHook(new Thread() {
                        public void run() {
                            try {
                                Files.delete(portFile);
                                log.println("Server terminated");
                            } catch (IOException e) {
                                e.printStackTrace(log);
                            }
                        }
                    });
                log.println("Server started on port " + socket.getLocalPort());
                for (;;) {
                    try {
                        final Socket client = socket.accept();
                        pool.execute(() -> handleClient(cmd, client, log));
                    } catch (IOException e) {
                        e.printStackTrace(log);
                    }
                }
            } catch (IOException e) {
                e.printStackTrace(log);
            } finally {
                socket.close();
                pool.shutdown();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
