package chasm;

public final class Escape {
    private Escape() {
    }

    private static StringBuilder escape(final StringBuilder r, final char c) {
        switch (c) {
        case '"':  return r.append("\\\"");
        case '\\': return r.append("\\\\");
        case '\b': return r.append("\\b");
        case '\f': return r.append("\\f");
        case '\n': return r.append("\\n");
        case '\r': return r.append("\\r");
        case '\t': return r.append("\\t");
        case '\0': return r.append("\\0");
        default:
            if (c >= 20 && c < 127)
                return r.append(c);
            r.append("\\u");
            for (int i = 0; i < 4; ++i) {
                final int m = (c >> ((3 - i) << 2)) & 0xF;
                r.append((char)(m >= 10 ? m - 10 + 'A' : m + '0'));
            }
            return r;
        }
    }

    public static String escapeChar(final char c) {
        return escape(new StringBuilder(), c).toString();
    }

    public static String escapeString(final String s) {
        final char[] cs = s.toCharArray();
        final StringBuilder r = new StringBuilder();
        for (final char c : cs)
            escape(r, c);
        return r.toString();
    }

    public static String unescapeString(final String s) {
        final StringBuilder r = new StringBuilder();
        final int len = s.length();
        for (int i = 0; i < len; ++i) {
            char c = s.charAt(i);
            if (c != '\\') {
                r.append(c);
            } else {
                if (++i >= len)
                    return null;
                switch (s.charAt(i)) {
                case '"':  r.append('"');  break;
                case '\\': r.append('\\'); break;
                case 'b':  r.append('\b'); break;
                case 'f':  r.append('\f'); break;
                case 'n':  r.append('\n'); break;
                case 'r':  r.append('\r'); break;
                case 't':  r.append('\t'); break;
                case '0':  r.append('\0'); break;
                case 'u':
                    final int end = i + 4;
                    if (end >= len)
                        return null;
                    int x = 0;
                    while (i < end) {
                        x <<= 4;
                        c = s.charAt(++i);
                        if (c >= '0' && c <= '9')
                            x += c - '0';
                        else if (c >= 'A' && c <= 'F')
                            x += 10 + c - 'A';
                        else if (c >= 'a' && c <= 'f')
                            x += 10 + c - 'a';
                        else
                            return null;
                    }
                    r.append((char)x);
                    break;
                default:
                    return null;
                }
            }
        }
        return r.toString();
    }
}
