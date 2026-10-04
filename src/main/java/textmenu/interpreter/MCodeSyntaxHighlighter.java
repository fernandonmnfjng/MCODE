package textmenu.interpreter;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.List;

public final class MCodeSyntaxHighlighter {

    private MCodeSyntaxHighlighter() {
    }

    // =========================================================
    // TOKEN
    // =========================================================

    public enum TokenType {
        WHITESPACE,
        COMMENT,

        STRING,
        NUMBER,

        KEYWORD,
        BOOLEAN,
        NULL,
        BUILTIN,
        TYPE,

        VARIABLE,
        FUNCTION,

        OPERATOR,
        DELIMITER,

        UNKNOWN
    }

    public record Token(
            String text,
            TokenType type,
            int start,
            int end
    ) {
    }

    // =========================================================
    // KEYWORDS
    // =========================================================

    private static final String[] KEYWORDS = {
            "if",
            "else",
            "elif",

            "while",
            "for",
            "in",

            "and",
            "or",
            "not",

            "def",
            "return",

            "import",
            "from",
            "as",

            "break",
            "continue",
            "pass",

            "class",

            "try",
            "except",
            "finally",
            "raise"
    };

    // =========================================================
    // BUILTINS
    // =========================================================

    private static final String[] BUILTINS = {
            "print",
            "len",
            "type",
            "int",
            "float",
            "str",
            "bool",
            "list",
            "tuple",
            "set",
            "dict",
            "abs",
            "round",
            "min",
            "max"
    };

    // =========================================================
    // TYPES
    // =========================================================

    private static final String[] TYPES = {
            "int",
            "float",
            "str",
            "bool",
            "list",
            "tuple",
            "set",
            "dict",
            "complex",
            "bytes",
            "bytearray",
            "range"
    };

    // =========================================================
    // COLORS
    // =========================================================

    private static final int COLOR_DEFAULT = 0xFFD4D4D4;
    private static final int COLOR_COMMENT = 0xFF6A9955;
    private static final int COLOR_STRING = 0xFFCE9178;
    private static final int COLOR_NUMBER = 0xFFB5CEA8;
    private static final int COLOR_KEYWORD = 0xFFC586C0;
    private static final int COLOR_BOOLEAN = 0xFF569CD6;
    private static final int COLOR_NULL = 0xFF569CD6;
    private static final int COLOR_BUILTIN = 0xFF4EC9B0;
    private static final int COLOR_TYPE = 0xFF4EC9B0;
    private static final int COLOR_VARIABLE = 0xFF9CDCFE;
    private static final int COLOR_FUNCTION = 0xFFDCDCAA;
    private static final int COLOR_OPERATOR = 0xFFD4D4D4;
    private static final int COLOR_DELIMITER = 0xFFD4D4D4;
    private static final int COLOR_UNKNOWN = 0xFFD4D4D4;

    // =========================================================
    // PUBLIC
    // =========================================================

    public static List<Token> tokenize(String line) {
        List<Token> tokens = new ArrayList<>();

        if (line == null || line.isEmpty()) {
            return tokens;
        }

        int i = 0;

        while (i < line.length()) {

            char c = line.charAt(i);

            // -------------------------------------------------
            // WHITESPACE
            // -------------------------------------------------

            if (Character.isWhitespace(c)) {
                int start = i;

                while (
                        i < line.length()
                                && Character.isWhitespace(line.charAt(i))
                ) {
                    i++;
                }

                tokens.add(
                        new Token(
                                line.substring(start, i),
                                TokenType.WHITESPACE,
                                start,
                                i
                        )
                );

                continue;
            }

            // -------------------------------------------------
            // COMMENT
            // -------------------------------------------------

            if (c == '#') {
                tokens.add(
                        new Token(
                                line.substring(i),
                                TokenType.COMMENT,
                                i,
                                line.length()
                        )
                );

                break;
            }

            // -------------------------------------------------
            // STRING
            // -------------------------------------------------

            if (c == '"' || c == '\'') {
                int start = i;
                char quote = c;

                // Triple quote
                if (
                        i + 2 < line.length()
                                && line.charAt(i + 1) == quote
                                && line.charAt(i + 2) == quote
                ) {
                    String delimiter = "" + quote + quote + quote;

                    i += 3;

                    while (i + 2 < line.length()) {
                        if (
                                line.charAt(i) == quote
                                        && line.charAt(i + 1) == quote
                                        && line.charAt(i + 2) == quote
                        ) {
                            i += 3;
                            break;
                        }

                        i++;
                    }
                } else {
                    i++;

                    while (i < line.length()) {
                        char current = line.charAt(i);

                        if (current == '\\') {
                            i += 2;
                            continue;
                        }

                        if (current == quote) {
                            i++;
                            break;
                        }

                        i++;
                    }
                }

                tokens.add(
                        new Token(
                                line.substring(start, i),
                                TokenType.STRING,
                                start,
                                i
                        )
                );

                continue;
            }

            // -------------------------------------------------
            // NUMBER
            // -------------------------------------------------

            if (Character.isDigit(c) || (c == '.' && i + 1 < line.length() && Character.isDigit(line.charAt(i + 1)))) {

                int start = i;

                // Hexadecimal
                if (
                        c == '0'
                                && i + 1 < line.length()
                                && (
                                line.charAt(i + 1) == 'x'
                                        || line.charAt(i + 1) == 'X'
                        )
                ) {
                    i += 2;

                    while (
                            i < line.length()
                                    && isHexDigit(line.charAt(i))
                    ) {
                        i++;
                    }
                }

                // Binary
                else if (
                        c == '0'
                                && i + 1 < line.length()
                                && (
                                line.charAt(i + 1) == 'b'
                                        || line.charAt(i + 1) == 'B'
                        )
                ) {
                    i += 2;

                    while (
                            i < line.length()
                                    && (
                                    line.charAt(i) == '0'
                                            || line.charAt(i) == '1'
                            )
                    ) {
                        i++;
                    }
                }

                // Octal
                else if (
                        c == '0'
                                && i + 1 < line.length()
                                && (
                                line.charAt(i + 1) == 'o'
                                        || line.charAt(i + 1) == 'O'
                        )
                ) {
                    i += 2;

                    while (
                            i < line.length()
                                    && line.charAt(i) >= '0'
                                    && line.charAt(i) <= '7'
                    ) {
                        i++;
                    }
                }

                // Normal decimal / float / exponent
                else {
                    boolean hasDot = false;
                    boolean hasExponent = false;

                    while (i < line.length()) {

                        char current = line.charAt(i);

                        if (Character.isDigit(current)) {
                            i++;
                            continue;
                        }

                        if (current == '.' && !hasDot) {
                            hasDot = true;
                            i++;
                            continue;
                        }

                        if (
                                (
                                        current == 'e'
                                                || current == 'E'
                                )
                                        && !hasExponent
                        ) {
                            hasExponent = true;
                            i++;

                            if (
                                    i < line.length()
                                            && (
                                            line.charAt(i) == '+'
                                                    || line.charAt(i) == '-'
                                    )
                            ) {
                                i++;
                            }

                            continue;
                        }

                        break;
                    }
                }

                tokens.add(
                        new Token(
                                line.substring(start, i),
                                TokenType.NUMBER,
                                start,
                                i
                        )
                );

                continue;
            }

            // -------------------------------------------------
            // IDENTIFIER
            // -------------------------------------------------

            if (isIdentifierStart(c)) {
                int start = i;

                i++;

                while (
                        i < line.length()
                                && isIdentifierPart(line.charAt(i))
                ) {
                    i++;
                }

                String word = line.substring(start, i);

                TokenType type = classifyIdentifier(
                        word,
                        line,
                        i
                );

                tokens.add(
                        new Token(
                                word,
                                type,
                                start,
                                i
                        )
                );

                continue;
            }

            // -------------------------------------------------
            // OPERATORS
            // -------------------------------------------------

            String operator = readOperator(line, i);

            if (operator != null) {
                tokens.add(
                        new Token(
                                operator,
                                TokenType.OPERATOR,
                                i,
                                i + operator.length()
                        )
                );

                i += operator.length();
                continue;
            }

            // -------------------------------------------------
            // DELIMITERS
            // -------------------------------------------------

            if (isDelimiter(c)) {
                tokens.add(
                        new Token(
                                String.valueOf(c),
                                TokenType.DELIMITER,
                                i,
                                i + 1
                        )
                );

                i++;
                continue;
            }

            // -------------------------------------------------
            // UNKNOWN
            // -------------------------------------------------

            tokens.add(
                    new Token(
                            String.valueOf(c),
                            TokenType.UNKNOWN,
                            i,
                            i + 1
                    )
            );

            i++;
        }

        return tokens;
    }

    // =========================================================
    // DRAW
    // =========================================================

    public static int drawLine(
            DrawContext context,
            TextRenderer textRenderer,
            String line,
            int x,
            int y
    ) {
        List<Token> tokens = tokenize(line);

        int drawX = x;

        for (Token token : tokens) {

            int color = getColor(token.type());

            context.drawText(
                    textRenderer,
                    token.text(),
                    drawX,
                    y,
                    color,
                    false
            );

            drawX += textRenderer.getWidth(token.text());
        }

        return drawX;
    }

    // =========================================================
    // COLOR
    // =========================================================

    public static int getColor(TokenType type) {
        return switch (type) {

            case COMMENT ->
                    COLOR_COMMENT;

            case STRING ->
                    COLOR_STRING;

            case NUMBER ->
                    COLOR_NUMBER;

            case KEYWORD ->
                    COLOR_KEYWORD;

            case BOOLEAN ->
                    COLOR_BOOLEAN;

            case NULL ->
                    COLOR_NULL;

            case BUILTIN ->
                    COLOR_BUILTIN;

            case TYPE ->
                    COLOR_TYPE;

            case VARIABLE ->
                    COLOR_VARIABLE;

            case FUNCTION ->
                    COLOR_FUNCTION;

            case OPERATOR ->
                    COLOR_OPERATOR;

            case DELIMITER ->
                    COLOR_DELIMITER;

            case UNKNOWN ->
                    COLOR_UNKNOWN;

            case WHITESPACE ->
                    COLOR_DEFAULT;
        };
    }

    // =========================================================
    // CLASSIFICATION
    // =========================================================

    private static TokenType classifyIdentifier(
            String word,
            String line,
            int end
    ) {

        if (word.equals("True") || word.equals("False")) {
            return TokenType.BOOLEAN;
        }

        if (word.equals("None")) {
            return TokenType.NULL;
        }

        if (contains(KEYWORDS, word)) {
            return TokenType.KEYWORD;
        }

        // La palabra fue definida como función de MCODE.
        if (contains(BUILTINS, word)) {
            return TokenType.BUILTIN;
        }

        if (contains(TYPES, word)) {
            return TokenType.TYPE;
        }

        // Una palabra seguida de '(' es llamada de función.
        int next = end;

        while (
                next < line.length()
                        && Character.isWhitespace(line.charAt(next))
        ) {
            next++;
        }

        if (
                next < line.length()
                        && line.charAt(next) == '('
        ) {
            return TokenType.FUNCTION;
        }

        return TokenType.VARIABLE;
    }

    // =========================================================
    // OPERATORS
    // =========================================================

    private static String readOperator(
            String line,
            int index
    ) {

        String[] operators = {
                "===",
                "!==",

                "==",
                "!=",
                "<=",
                ">=",

                "//",
                "**",

                "+=",
                "-=",
                "*=",
                "/=",
                "%=",

                "->",
                ":=",

                "<<",
                ">>",

                "&=",
                "|=",
                "^=",

                "+",
                "-",
                "*",
                "/",
                "%",
                "=",

                "<",
                ">",

                "&",
                "|",
                "^",
                "~",

                "!"
        };

        for (String operator : operators) {
            if (
                    line.startsWith(
                            operator,
                            index
                    )
            ) {
                return operator;
            }
        }

        return null;
    }

    // =========================================================
    // DELIMITERS
    // =========================================================

    private static boolean isDelimiter(char c) {
        return switch (c) {
            case '(',
                 ')',
                 '[',
                 ']',
                 '{',
                 '}',
                 ',',
                 ':',
                 ';',
                 '.' ->
                    true;

            default ->
                    false;
        };
    }

    // =========================================================
    // IDENTIFIERS
    // =========================================================

    private static boolean isIdentifierStart(char c) {
        return Character.isLetter(c) || c == '_';
    }

    private static boolean isIdentifierPart(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }

    // =========================================================
    // HEX
    // =========================================================

    private static boolean isHexDigit(char c) {
        return (
                Character.isDigit(c)
                        || (c >= 'a' && c <= 'f')
                        || (c >= 'A' && c <= 'F')
        );
    }

    // =========================================================
    // ARRAY SEARCH
    // =========================================================

    private static boolean contains(
            String[] array,
            String value
    ) {
        for (String item : array) {
            if (item.equals(value)) {
                return true;
            }
        }

        return false;
    }
}