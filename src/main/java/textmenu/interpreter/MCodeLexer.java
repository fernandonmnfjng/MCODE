package textmenu.interpreter;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Set;

public final class MCodeLexer {
    private static final Set<String> KEYWORDS = Set.of(
            "if", "elif", "else", "while", "for", "in", "and", "or", "not", "is",
            "def", "return", "break", "continue", "pass", "class", "try", "except",
            "finally", "raise", "global", "nonlocal", "lambda", "True", "False", "None",
            "as", "with", "yield", "from", "import", "async", "await", "match", "case"
    );

    private static final String[] OPERATORS = {
            ">>>", "**=", "//=", "<<=", ">>=", "+=", "-=", "*=", "/=", "%=", "@=", "&=", "|=", "^=",
            "==", "!=", "<=", ">=", "//", "**", "<<", ">>", "->", "+", "-", "*", "/", "%", "@",
            "=", "<", ">", "&", "|", "^", "~", ":", "."
    };

    public List<MCodeToken> tokenize(String source) {
        List<MCodeToken> tokens = new ArrayList<>();
        String normalized = source == null ? "" : source.replace("\r\n", "\n").replace('\r', '\n');
        String[] lines = normalized.split("\\n", -1);
        Deque<Integer> indents = new ArrayDeque<>();
        indents.push(0);
        int parenDepth = 0;

        for (int lineNo = 0; lineNo < lines.length; lineNo++) {
            String line = lines[lineNo];
            int indent = 0;
            while (indent < line.length() && (line.charAt(indent) == ' ' || line.charAt(indent) == '\t')) indent += line.charAt(indent) == '\t' ? 4 : 1;
            String content = line.substring(indent);
            if (content.trim().isEmpty() || content.trim().startsWith("#")) continue;

            if (parenDepth == 0) {
                int current = indents.peek();
                if (indent > current) {
                    indents.push(indent);
                    tokens.add(new MCodeToken(MCodeToken.Type.INDENT, "", lineNo + 1, 1));
                } else {
                    while (indent < indents.peek()) {
                        indents.pop();
                        tokens.add(new MCodeToken(MCodeToken.Type.DEDENT, "", lineNo + 1, 1));
                    }
                    if (indent != indents.peek()) throw error(lineNo + 1, 1, "Indentación inconsistente");
                }
            }

            int i = indent;
            while (i < line.length()) {
                char c = line.charAt(i);
                if (c == ' ' || c == '\t') { i++; continue; }
                if (c == '#') break;

                if (Character.isLetter(c) || c == '_') {
                    int start = i++;
                    while (i < line.length() && (Character.isLetterOrDigit(line.charAt(i)) || line.charAt(i) == '_')) i++;
                    String text = line.substring(start, i);
                    tokens.add(new MCodeToken(KEYWORDS.contains(text) ? MCodeToken.Type.KEYWORD : MCodeToken.Type.IDENTIFIER, text, lineNo + 1, start + 1));
                    continue;
                }

                if (Character.isDigit(c) || (c == '.' && i + 1 < line.length() && Character.isDigit(line.charAt(i + 1)))) {
                    int start = i;
                    i = readNumber(line, i);
                    tokens.add(new MCodeToken(MCodeToken.Type.NUMBER, line.substring(start, i), lineNo + 1, start + 1));
                    continue;
                }

                if (c == '\'' || c == '"') {
                    int start = i;
                    char quote = c;
                    i++;
                    boolean triple = i + 1 < line.length() && line.charAt(i) == quote && line.charAt(i + 1) == quote;
                    if (triple) i += 2;
                    while (i < line.length()) {
                        char current = line.charAt(i);
                        if (current == '\\') { i += 2; continue; }
                        if (!triple && current == quote) { i++; break; }
                        if (triple && i + 2 < line.length() && line.charAt(i) == quote && line.charAt(i + 1) == quote && line.charAt(i + 2) == quote) { i += 3; break; }
                        i++;
                    }
                    if ((!triple && (i == line.length() && line.charAt(i - 1) != quote)) || (triple && i > line.length())) throw error(lineNo + 1, start + 1, "String sin cerrar");
                    tokens.add(new MCodeToken(MCodeToken.Type.STRING, line.substring(start, i), lineNo + 1, start + 1));
                    continue;
                }

                String op = readOperator(line, i);
                if (op != null) {
                    tokens.add(new MCodeToken(MCodeToken.Type.OPERATOR, op, lineNo + 1, i + 1));
                    if (op.equals("(") || op.equals("[") || op.equals("{")) parenDepth++;
                    i += op.length();
                    continue;
                }

                if (isDelimiter(c)) {
                    tokens.add(new MCodeToken(MCodeToken.Type.DELIMITER, String.valueOf(c), lineNo + 1, i + 1));
                    if (c == '(' || c == '[' || c == '{') parenDepth++;
                    if (c == ')' || c == ']' || c == '}') parenDepth = Math.max(0, parenDepth - 1);
                    i++;
                    continue;
                }

                throw error(lineNo + 1, i + 1, "Carácter inesperado: " + c);
            }
            if (parenDepth == 0) tokens.add(new MCodeToken(MCodeToken.Type.NEWLINE, "", lineNo + 1, line.length() + 1));
        }

        while (indents.size() > 1) {
            indents.pop();
            tokens.add(new MCodeToken(MCodeToken.Type.DEDENT, "", lines.length, 1));
        }
        tokens.add(new MCodeToken(MCodeToken.Type.EOF, "", Math.max(1, lines.length), 1));
        return tokens;
    }

    private static int readNumber(String line, int i) {
        if (i + 1 < line.length() && line.charAt(i) == '0' && "xXbBoO".indexOf(line.charAt(i + 1)) >= 0) {
            char base = line.charAt(i + 1);
            i += 2;
            while (i < line.length()) {
                char c = line.charAt(i);
                boolean ok = switch (base) {
                    case 'x', 'X' -> Character.digit(c, 16) >= 0 || c == '_';
                    case 'b', 'B' -> c == '0' || c == '1' || c == '_';
                    case 'o', 'O' -> (c >= '0' && c <= '7') || c == '_';
                    default -> false;
                };
                if (!ok) break;
                i++;
            }
            return i;
        }
        boolean dot = false, exp = false;
        while (i < line.length()) {
            char c = line.charAt(i);
            if (Character.isDigit(c) || c == '_') i++;
            else if (c == '.' && !dot && !exp) { dot = true; i++; }
            else if ((c == 'e' || c == 'E') && !exp) {
                exp = true; i++;
                if (i < line.length() && (line.charAt(i) == '+' || line.charAt(i) == '-')) i++;
            } else break;
        }
        return i;
    }

    private static String readOperator(String line, int index) {
        for (String op : OPERATORS) if (line.startsWith(op, index)) return op;
        return null;
    }

    private static boolean isDelimiter(char c) {
        return switch (c) { case '(', ')', '[', ']', '{', '}', ',', ';' -> true; default -> false; };
    }

    private static RuntimeException error(int line, int column, String message) {
        return new IllegalArgumentException("Línea " + line + ", columna " + column + ": " + message);
    }
}
