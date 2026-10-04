package textmenu.interpreter;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Set;

public final class MCodeLexer {
    private static final Set<String> KEYWORDS = Set.of(
            "and", "as", "assert", "async", "await", "break", "case", "class", "continue",
            "def", "del", "elif", "else", "except", "False", "finally", "for", "from", "global",
            "if", "import", "in", "is", "lambda", "match", "None", "nonlocal", "not", "or",
            "pass", "raise", "return", "True", "try", "while", "with", "yield"
    );

    private static final String[] OPERATORS = {
            "**=", "//=", ">>=", "<<=", "...",
            "==", "!=", "<=", ">=", "//", "**", "+=", "-=", "*=", "/=", "%=",
            "&=", "|=", "^=", ">>", "<<", "->", ":=", "@=",
            "+", "-", "*", "/", "%", "=", "<", ">", "&", "|", "^", "~", "@"
    };

    private static final Set<Character> DELIMITERS = Set.of(
            '(', ')', '[', ']', '{', '}', ',', ':', '.', ';'
    );

    public List<MCodeToken> tokenize(String source) {
        List<MCodeToken> tokens = new ArrayList<>();
        Deque<Integer> indents = new ArrayDeque<>();
        indents.push(0);

        String normalized = source == null ? "" : source.replace("\r\n", "\n").replace('\r', '\n');
        String[] lines = normalized.split("\n", -1);
        int bracketDepth = 0;

        for (int lineIndex = 0; lineIndex < lines.length; lineIndex++) {
            String line = lines[lineIndex];
            int lineNumber = lineIndex + 1;
            int contentStart = countIndent(line);
            String content = line.substring(contentStart);

            if (content.isBlank() || content.stripLeading().startsWith("#")) {
                continue;
            }

            if (bracketDepth == 0) {
                int indent = indentationWidth(line.substring(0, contentStart));
                int current = indents.peek();

                if (indent > current) {
                    indents.push(indent);
                    tokens.add(new MCodeToken(MCodeToken.Type.INDENT, "", lineNumber, 1));
                } else if (indent < current) {
                    while (indents.size() > 1 && indent < indents.peek()) {
                        indents.pop();
                        tokens.add(new MCodeToken(MCodeToken.Type.DEDENT, "", lineNumber, 1));
                    }
                    if (indent != indents.peek()) {
                        throw error("Indentación inconsistente", lineNumber, 1);
                    }
                }
            }

            scanLine(content, lineNumber, contentStart + 1, tokens, bracketDepthHolder -> {});

            // Recompute bracket depth from the tokens added by this line.
            for (int i = tokens.size() - 1; i >= 0; i--) {
                MCodeToken token = tokens.get(i);
                if (token.line() != lineNumber) break;
                if (token.type() == MCodeToken.Type.DELIMITER) {
                    switch (token.text()) {
                        case "(", "[", "{" -> bracketDepth++;
                        case ")", "]", "}" -> bracketDepth--;
                        default -> { }
                    }
                }
            }

            if (bracketDepth == 0) {
                tokens.add(new MCodeToken(MCodeToken.Type.NEWLINE, "", lineNumber, line.length() + 1));
            }
        }

        while (indents.size() > 1) {
            indents.pop();
            tokens.add(new MCodeToken(MCodeToken.Type.DEDENT, "", lines.length, 1));
        }

        tokens.add(new MCodeToken(MCodeToken.Type.EOF, "", Math.max(1, lines.length), 1));
        return tokens;
    }

    private void scanLine(String line, int lineNumber, int columnOffset, List<MCodeToken> tokens, java.util.function.Consumer<Object> ignored) {
        int i = 0;
        while (i < line.length()) {
            char c = line.charAt(i);
            int column = columnOffset + i;

            if (Character.isWhitespace(c)) {
                i++;
                continue;
            }

            if (c == '#') {
                break;
            }

            if (c == '\'' || c == '"') {
                int start = i;
                char quote = c;
                i++;
                boolean closed = false;
                while (i < line.length()) {
                    char current = line.charAt(i);
                    if (current == '\\') {
                        i += Math.min(2, line.length() - i);
                    } else if (current == quote) {
                        i++;
                        closed = true;
                        break;
                    } else {
                        i++;
                    }
                }
                if (!closed) throw error("String sin cerrar", lineNumber, column);
                tokens.add(new MCodeToken(MCodeToken.Type.STRING, line.substring(start, i), lineNumber, column));
                continue;
            }

            if (Character.isDigit(c) || (c == '.' && i + 1 < line.length() && Character.isDigit(line.charAt(i + 1)))) {
                int start = i;
                i = scanNumber(line, i);
                tokens.add(new MCodeToken(MCodeToken.Type.NUMBER, line.substring(start, i), lineNumber, column));
                continue;
            }

            if (Character.isLetter(c) || c == '_') {
                int start = i++;
                while (i < line.length() && (Character.isLetterOrDigit(line.charAt(i)) || line.charAt(i) == '_')) i++;
                String word = line.substring(start, i);
                MCodeToken.Type type = KEYWORDS.contains(word) ? MCodeToken.Type.KEYWORD : MCodeToken.Type.IDENTIFIER;
                tokens.add(new MCodeToken(type, word, lineNumber, column));
                continue;
            }

            String operator = readOperator(line, i);
            if (operator != null) {
                tokens.add(new MCodeToken(MCodeToken.Type.OPERATOR, operator, lineNumber, column));
                i += operator.length();
                continue;
            }

            if (DELIMITERS.contains(c)) {
                tokens.add(new MCodeToken(MCodeToken.Type.DELIMITER, String.valueOf(c), lineNumber, column));
                i++;
                continue;
            }

            throw error("Carácter no reconocido: '" + c + "'", lineNumber, column);
        }
    }

    private static int scanNumber(String line, int i) {
        if (i + 1 < line.length() && line.charAt(i) == '0') {
            char base = line.charAt(i + 1);
            if (base == 'x' || base == 'X') {
                i += 2;
                while (i < line.length() && isHex(line.charAt(i))) i++;
                return i;
            }
            if (base == 'b' || base == 'B') {
                i += 2;
                while (i < line.length() && (line.charAt(i) == '0' || line.charAt(i) == '1')) i++;
                return i;
            }
            if (base == 'o' || base == 'O') {
                i += 2;
                while (i < line.length() && line.charAt(i) >= '0' && line.charAt(i) <= '7') i++;
                return i;
            }
        }

        boolean dot = false;
        boolean exponent = false;
        while (i < line.length()) {
            char c = line.charAt(i);
            if (Character.isDigit(c) || c == '_') {
                i++;
            } else if (c == '.' && !dot && !exponent) {
                dot = true;
                i++;
            } else if ((c == 'e' || c == 'E') && !exponent) {
                exponent = true;
                i++;
                if (i < line.length() && (line.charAt(i) == '+' || line.charAt(i) == '-')) i++;
            } else {
                break;
            }
        }
        return i;
    }

    private static String readOperator(String line, int index) {
        for (String operator : OPERATORS) {
            if (line.startsWith(operator, index)) return operator;
        }
        return null;
    }

    private static int countIndent(String line) {
        int i = 0;
        while (i < line.length() && (line.charAt(i) == ' ' || line.charAt(i) == '\t')) i++;
        return i;
    }

    private static int indentationWidth(String whitespace) {
        int width = 0;
        for (char c : whitespace.toCharArray()) {
            if (c == '\t') width += 8;
            else width++;
        }
        return width;
    }

    private static boolean isHex(char c) {
        return Character.isDigit(c) || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
    }

    private static RuntimeException error(String message, int line, int column) {
        return new IllegalArgumentException("Línea " + line + ", columna " + column + ": " + message);
    }
}
