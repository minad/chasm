package chasm;

import java.io.IOException;
import java.io.OutputStream;

public final class TeeOutputStream extends OutputStream {
    private final OutputStream a, b;

    public TeeOutputStream(final OutputStream sa, final OutputStream sb) {
        a = sa;
        b = sb;
    }

    @Override
    public void write(final int data) throws IOException {
        a.write(data);
        b.write(data);
    }

    @Override
    public void write(final byte[] data) throws IOException {
        a.write(data);
        b.write(data);
    }

    @Override
    public void write(final byte[] data, final int off, final int len) throws IOException {
        a.write(data, off, len);
        b.write(data, off, len);
    }

    @Override
    public void flush() throws IOException {
        a.flush();
        b.flush();
    }

    @Override
    public void close() throws IOException {
        a.close();
        b.close();
    }
}
