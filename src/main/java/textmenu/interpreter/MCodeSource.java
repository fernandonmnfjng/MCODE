package textmenu.interpreter;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Metadata directives are only recognized on the first physical line. */
public final class MCodeSource {
    private static final Pattern PATH = Pattern.compile("^\\s*SetPATH\\s+(.+?)\\s*$", Pattern.CASE_INSENSITIVE);
    private MCodeSource() {}
    public record Parsed(String path, String code) {}
    public static Parsed parse(String source) {
        String text = source == null ? "" : source.replace("\r\n", "\n").replace('\r', '\n');
        int nl = text.indexOf('\n');
        String first = nl < 0 ? text : text.substring(0, nl);
        Matcher m = PATH.matcher(first);
        if (!m.matches()) return new Parsed(null, text);
        String path = m.group(1).trim();
        if ((path.startsWith("\"") && path.endsWith("\"")) || (path.startsWith("'") && path.endsWith("'"))) path = path.substring(1, path.length()-1);
        if (path.isBlank()) throw new IllegalArgumentException("SetPATH necesita un nombre/ruta no vacía");
        return new Parsed(path, nl < 0 ? "" : text.substring(nl + 1));
    }
    public static String withPath(String path, String code) {
        String body = parse(code).code();
        return path == null || path.isBlank() ? body : "SetPATH " + path + "\n" + body;
    }
}
