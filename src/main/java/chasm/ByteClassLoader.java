package chasm;

public final class ByteClassLoader extends ClassLoader {
    public Class<?> loadClass(final byte[] data) {
        Class<?> c = defineClass(null, data, 0, data.length);
        resolveClass(c);
        return c;
    }
}
