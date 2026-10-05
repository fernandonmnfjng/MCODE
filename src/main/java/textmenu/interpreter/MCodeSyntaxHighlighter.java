package textmenu.interpreter;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class MCodeSyntaxHighlighter {
    private MCodeSyntaxHighlighter() {}

    public enum TokenType {
        WHITESPACE, COMMENT, STRING, NUMBER,
        KEYWORD, BOOLEAN, NULL, BUILTIN, TYPE,
        VARIABLE, FUNCTION, OPERATOR, DELIMITER, UNKNOWN
    }

    public record Token(String text, TokenType type, int start, int end) {}

    private static final Set<String> KEYWORDS = Set.of(
            "if","elif","else","while","for","in","and","or","not","is",
            "def","return","break","continue","pass","class","try","except",
            "finally","raise","global","nonlocal","lambda","as","with","yield",
            "from","import","async","await","match","case"
    );

    private static final Set<String> BUILTINS = Set.of(
            "print","len","type","int","float","str","bool","list","tuple","set","dict",
            "range","abs","round","min","max","sum","any","all","enumerate","zip",
            "sorted","reversed","isinstance","issubclass","repr","ascii","format","hex","oct",
        "bin","ord","chr","hash","id","callable","dir","getattr","setattr","hasattr",
        "delattr","vars","object","super","sleep","pow","divmod","iter","next","map","filter"
    );

    private static final Set<String> TYPES = Set.of(
            "int","float","str","bool","list","tuple","set","dict","range","complex","bytes","bytearray",
        "object","BaseException","Exception","ArithmeticError","ZeroDivisionError","ValueError","TypeError",
        "NameError","IndexError","KeyError","AttributeError","SyntaxError","ImportError","ModuleNotFoundError"
    );

    private static final int DEFAULT = 0xFFD4D4D4;
    private static final int COMMENT = 0xFF6A9955;
    private static final int STRING = 0xFFCE9178;
    private static final int NUMBER = 0xFFB5CEA8;
    private static final int KEYWORD = 0xFFC586C0;
    private static final int BOOLEAN = 0xFF569CD6;
    private static final int NULL = 0xFF569CD6;
    private static final int BUILTIN = 0xFF4EC9B0;
    private static final int TYPE = 0xFF4EC9B0;
    private static final int VARIABLE = 0xFF9CDCFE;
    private static final int FUNCTION = 0xFFDCDCAA;
    private static final int OPERATOR = 0xFFD4D4D4;
    private static final int DELIMITER = 0xFFD4D4D4;

    public static List<Token> tokenize(String line) {
        List<Token> out = new ArrayList<>();
        if (line == null || line.isEmpty()) return out;
        int i = 0;
        while (i < line.length()) {
            char c = line.charAt(i);
            if (Character.isWhitespace(c)) {
                int start = i++;
                while (i < line.length() && Character.isWhitespace(line.charAt(i))) i++;
                out.add(new Token(line.substring(start, i), TokenType.WHITESPACE, start, i));
                continue;
            }
            if (c == '#') {
                out.add(new Token(line.substring(i), TokenType.COMMENT, i, line.length()));
                break;
            }
            if (c == '"' || c == '\'') {
                int start = i;
                char quote = c;
                i++;
                while (i < line.length()) {
                    char q = line.charAt(i);
                    if (q == '\\') { i += Math.min(2, line.length() - i); continue; }
                    if (q == quote) { i++; break; }
                    i++;
                }
                out.add(new Token(line.substring(start, i), TokenType.STRING, start, i));
                continue;
            }
            if (Character.isDigit(c) || (c == '.' && i + 1 < line.length() && Character.isDigit(line.charAt(i + 1)))) {
                int start = i++;
                while (i < line.length()) {
                    char q = line.charAt(i);
                    if (Character.isLetterOrDigit(q) || q == '.' || q == '_') i++; else break;
                }
                out.add(new Token(line.substring(start, i), TokenType.NUMBER, start, i));
                continue;
            }
            if (Character.isLetter(c) || c == '_') {
                int start = i++;
                while (i < line.length() && (Character.isLetterOrDigit(line.charAt(i)) || line.charAt(i) == '_')) i++;
                String word = line.substring(start, i);
                TokenType type;
                if (word.equals("True") || word.equals("False")) type = TokenType.BOOLEAN;
                else if (word.equals("None")) type = TokenType.NULL;
                else if (KEYWORDS.contains(word)) type = TokenType.KEYWORD;
                else if (BUILTINS.contains(word)) type = TokenType.BUILTIN;
                else if (TYPES.contains(word)) type = TokenType.TYPE;
                else {
                    int next = i;
                    while (next < line.length() && Character.isWhitespace(line.charAt(next))) next++;
                    type = next < line.length() && line.charAt(next) == '(' ? TokenType.FUNCTION : TokenType.VARIABLE;
                }
                out.add(new Token(word, type, start, i));
                continue;
            }
            String op = operatorAt(line, i);
            if (op != null) {
                out.add(new Token(op, TokenType.OPERATOR, i, i + op.length()));
                i += op.length();
                continue;
            }
            out.add(new Token(String.valueOf(c), isDelimiter(c) ? TokenType.DELIMITER : TokenType.UNKNOWN, i, i + 1));
            i++;
        }
        return out;
    }

    public static int getColor(TokenType type) {
        return switch (type) {
            case COMMENT -> COMMENT;
            case STRING -> STRING;
            case NUMBER -> NUMBER;
            case KEYWORD -> KEYWORD;
            case BOOLEAN -> BOOLEAN;
            case NULL -> NULL;
            case BUILTIN -> BUILTIN;
            case TYPE -> TYPE;
            case VARIABLE -> VARIABLE;
            case FUNCTION -> FUNCTION;
            case OPERATOR -> OPERATOR;
            case DELIMITER -> DELIMITER;
            default -> DEFAULT;
        };
    }

    private static boolean isDelimiter(char c) {
        return c == '(' || c == ')' || c == '[' || c == ']' || c == '{' || c == '}' || c == ',' || c == ':' || c == ';' || c == '.';
    }

    private static String operatorAt(String line, int i) {
        String[] ops = {"**=","//=","<<=",">>=","+=","-=","*=","/=","%=","@=","&=","|=","^=","==","!=","<=",">=","//","**","<<",">>","->",":=","+","-","*","/","%","@","=","<",">","&","|","^","~","!"};
        for (String op : ops) if (line.startsWith(op, i)) return op;
        return null;
    }
}
