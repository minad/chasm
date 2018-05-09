package chasm;

import org.junit.Test;

public final class EscapeTest {
    @Test
    public void randomTest() {
        for (int runs = 0; runs < 100000; ++runs) {
            final int len = (int)(Math.random() * 100);
            final char[] c = new char[len];
            for (int i = 0; i < len; ++i) {
                c[i] = (char)(Math.random() * 0xFFFF);
                final String t = Escape.escapeChar(c[i]), u = Escape.unescapeString(t);
                if (u == null || u.length() != 1 || u.charAt(0) != c[i])
                    throw new RuntimeException(c[i] + " -> " + t + " -> " + u);
            }
            final String s = new String(c), t = Escape.escapeString(s), u = Escape.unescapeString(t);
            if (u == null || !s.equals(u))
                throw new RuntimeException(s + " -> " + t + " -> " + u);
        }
    }

    @Test
    public void specialTest() {
        final String s = "\\\"\b\n\t\f\r\0", t = Escape.escapeString(s), u = Escape.unescapeString(t);
        if (u == null || !s.equals(u))
            throw new RuntimeException(s + " -> " + t + " -> " + u);
    }
}
