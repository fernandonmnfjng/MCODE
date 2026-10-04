package textmenu.interpreter;

public record MCodeToken(
        Type type,
        String text,
        int line,
        int column
) {
    public enum Type {
        IDENTIFIER,
        NUMBER,
        STRING,
        KEYWORD,
        OPERATOR,
        DELIMITER,
        NEWLINE,
        INDENT,
        DEDENT,
        EOF
    }
}
