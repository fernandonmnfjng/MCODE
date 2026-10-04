package textmenu.interpreter;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import static textmenu.interpreter.MCodeAst.*;

public final class MCodeParser {
    private final List<MCodeToken> tokens;
    private int position;

    public MCodeParser(List<MCodeToken> tokens) {
        this.tokens = tokens;
    }

    public Program parse() {
        List<Statement> statements = new ArrayList<>();
        skipNewlines();
        while (!check(MCodeToken.Type.EOF)) {
            if (match(MCodeToken.Type.DEDENT)) {
                error(previous(), "DEDENT inesperado");
            }
            statements.add(parseStatement());
            while (match(MCodeToken.Type.NEWLINE)) {}
        }
        return new Program(List.copyOf(statements));
    }

    private Statement parseStatement() {
        if (checkKeyword("if") || checkKeyword("elif") || checkKeyword("else") || checkKeyword("while") ||
                checkKeyword("for") || checkKeyword("def") || checkKeyword("class") || checkKeyword("try") ||
                checkKeyword("with") || checkKeyword("match")) {
            error(peek(), "Esta sentencia pertenece a una fase posterior del lenguaje");
        }

        Expression first = parseExpression();
        List<Expression> targets = new ArrayList<>();
        targets.add(first);

        // Tuple/list unpacking sin paréntesis: a, b = 1, 2
        if (matchText(",")) {
            while (true) {
                targets.add(parseExpression());
                if (!matchText(",")) break;
                if (checkText("=") || check(MCodeToken.Type.NEWLINE) || check(MCodeToken.Type.EOF)) break;
            }
        }

        if (matchText("=")) {
            boolean unpackedTarget = targets.size() > 1;
            validateTargets(targets);
            List<Expression> assignmentTargets = unpackedTarget
                    ? List.of(new TupleExpr(List.copyOf(targets)))
                    : new ArrayList<>(targets);

            while (true) {
                Expression value = parseExpression();

                // a = b = 1
                if (matchText("=")) {
                    if (unpackedTarget) {
                        error(previous(), "No se puede encadenar esta asignación estructurada");
                    }
                    assignmentTargets.add(requireTarget(value));
                    continue;
                }

                // a = 1, 2, 3
                if (matchText(",")) {
                    List<Expression> values = new ArrayList<>();
                    values.add(value);
                    do {
                        values.add(parseExpression());
                    } while (matchText(",") && !check(MCodeToken.Type.NEWLINE) && !check(MCodeToken.Type.EOF));
                    value = new TupleExpr(List.copyOf(values));
                }

                validateTargets(assignmentTargets);
                return new Assignment(List.copyOf(assignmentTargets), value);
            }
        }

        if (targets.size() > 1) {
            error(peek(), "Se esperaba '=' después de la asignación múltiple");
        }

        if (isAugmentedOperator(peek().text())) {
            String operator = advance().text();
            validateTarget(first);
            Expression value = parseExpression();
            return new AugmentedAssignment(first, operator.substring(0, operator.length() - 1), value);
        }

        return new ExpressionStatement(first);
    }

    private Expression parseExpression() {
        return parseOr();
    }

    private Expression parseOr() {
        Expression expression = parseAnd();
        while (matchKeyword("or")) expression = new Binary(expression, "or", parseAnd());
        return expression;
    }

    private Expression parseAnd() {
        Expression expression = parseNot();
        while (matchKeyword("and")) expression = new Binary(expression, "and", parseNot());
        return expression;
    }

    private Expression parseNot() {
        if (matchKeyword("not")) return new Unary("not", parseNot());
        return parseComparison();
    }

    private Expression parseComparison() {
        Expression left = parseBitOr();
        List<String> operators = new ArrayList<>();
        List<Expression> rights = new ArrayList<>();

        while (true) {
            if (matchText("==")) operators.add("==");
            else if (matchText("!=")) operators.add("!=");
            else if (matchText("<=")) operators.add("<=");
            else if (matchText(">=")) operators.add(">=");
            else if (matchText("<")) operators.add("<");
            else if (matchText(">")) operators.add(">");
            else if (matchKeyword("in")) operators.add("in");
            else if (matchKeyword("is")) {
                if (matchKeyword("not")) operators.add("is not");
                else operators.add("is");
            } else if (matchKeyword("not")) {
                if (matchKeyword("in")) operators.add("not in");
                else error(previous(), "Se esperaba 'in' después de 'not'");
            } else {
                break;
            }
            rights.add(parseBitOr());
        }

        if (operators.isEmpty()) return left;
        return new Comparison(left, List.copyOf(operators), List.copyOf(rights));
    }

    private Expression parseBitOr() {
        Expression expression = parseBitXor();
        while (matchText("|")) expression = new Binary(expression, "|", parseBitXor());
        return expression;
    }

    private Expression parseBitXor() {
        Expression expression = parseBitAnd();
        while (matchText("^")) expression = new Binary(expression, "^", parseBitAnd());
        return expression;
    }

    private Expression parseBitAnd() {
        Expression expression = parseShift();
        while (matchText("&")) expression = new Binary(expression, "&", parseShift());
        return expression;
    }

    private Expression parseShift() {
        Expression expression = parseTerm();
        while (checkText("<<") || checkText(">>")) {
            String op = advance().text();
            expression = new Binary(expression, op, parseTerm());
        }
        return expression;
    }

    private Expression parseTerm() {
        Expression expression = parseFactor();
        while (checkText("+") || checkText("-")) {
            String op = advance().text();
            expression = new Binary(expression, op, parseFactor());
        }
        return expression;
    }

    private Expression parseFactor() {
        Expression expression = parseUnary();
        while (checkText("*") || checkText("/") || checkText("//") || checkText("%") || checkText("@")) {
            String op = advance().text();
            expression = new Binary(expression, op, parseUnary());
        }
        return expression;
    }

    private Expression parseUnary() {
        if (matchText("+")) return new Unary("+", parseUnary());
        if (matchText("-")) return new Unary("-", parseUnary());
        if (matchText("~")) return new Unary("~", parseUnary());
        return parsePower();
    }

    private Expression parsePower() {
        Expression expression = parsePostfix();
        if (matchText("**")) expression = new Binary(expression, "**", parseUnary());
        return expression;
    }

    private Expression parsePostfix() {
        Expression expression = parsePrimary();
        while (true) {
            if (matchText("(")) {
                expression = new Call(expression, parseArguments());
                continue;
            }

            if (matchText("[")) {
                Expression subscript = parseSubscript();
                consumeText("]", "Se esperaba ']'");
                expression = new Subscript(expression, subscript);
                continue;
            }

            break;
        }
        return expression;
    }

    private List<CallArg> parseArguments() {
        List<CallArg> arguments = new ArrayList<>();
        if (matchText(")")) return arguments;

        do {
            if (matchText("**")) {
                arguments.add(new CallArg(null, parseExpression(), false, true));
            } else if (matchText("*")) {
                arguments.add(new CallArg(null, parseExpression(), true, false));
            } else {
                Expression value = parseExpression();
                if (value instanceof Name name && matchText("=")) {
                    arguments.add(new CallArg(name.name(), parseExpression(), false, false));
                } else {
                    arguments.add(new CallArg(null, value, false, false));
                }
            }
        } while (matchText(","));

        consumeText(")", "Se esperaba ')'");
        return List.copyOf(arguments);
    }

    private Expression parseSubscript() {
        Expression start = null;
        Expression stop = null;
        Expression step = null;

        if (!checkText(":") && !checkText("]")) start = parseExpression();
        if (matchText(":")) {
            if (!checkText(":") && !checkText("]")) stop = parseExpression();
            if (matchText(":")) {
                if (!checkText("]")) step = parseExpression();
            }
            return new Slice(start, stop, step);
        }

        if (start == null) error(peek(), "Índice vacío");
        return start;
    }

    private Expression parsePrimary() {
        MCodeToken token = advance();

        if (token.type() == MCodeToken.Type.NUMBER) return new Literal(parseNumber(token));
        if (token.type() == MCodeToken.Type.STRING) return new Literal(parseString(token));

        if (token.type() == MCodeToken.Type.KEYWORD) {
            return switch (token.text()) {
                case "True" -> new Literal(Boolean.TRUE);
                case "False" -> new Literal(Boolean.FALSE);
                case "None" -> new Literal(null);
                default -> {
                    error(token, "Se esperaba una expresión, no '" + token.text() + "'");
                    yield null;
                }
            };
        }

        if (token.type() == MCodeToken.Type.IDENTIFIER) return new Name(token.text());

        if (token.text().equals("(")) return parseParenthesized();
        if (token.text().equals("[")) return parseList();
        if (token.text().equals("{")) return parseBraceLiteral();

        error(token, "Se esperaba una expresión");
        return null;
    }

    private Expression parseParenthesized() {
        if (matchText(")")) return new TupleExpr(List.of());

        Expression first = parseExpression();
        if (!matchText(",")) {
            consumeText(")", "Se esperaba ')'");
            return first;
        }

        List<Expression> elements = new ArrayList<>();
        elements.add(first);
        if (!checkText(")")) {
            do {
                elements.add(parseExpression());
            } while (matchText(",") && !checkText(")"));
        }
        consumeText(")", "Se esperaba ')'");
        return new TupleExpr(List.copyOf(elements));
    }

    private Expression parseList() {
        List<Expression> elements = new ArrayList<>();
        if (matchText("]")) return new ListExpr(List.of());
        do {
            elements.add(parseExpression());
        } while (matchText(",") && !checkText("]"));
        consumeText("]", "Se esperaba ']'");
        return new ListExpr(List.copyOf(elements));
    }

    private Expression parseBraceLiteral() {
        if (matchText("}")) return new DictExpr(List.of());

        Expression first = parseExpression();

        if (matchText(":")) {
            List<Entry> entries = new ArrayList<>();
            entries.add(new Entry(first, parseExpression()));
            while (matchText(",") && !checkText("}")) {
                Expression key = parseExpression();
                consumeText(":", "Se esperaba ':' en el diccionario");
                entries.add(new Entry(key, parseExpression()));
            }
            consumeText("}", "Se esperaba '}'");
            return new DictExpr(List.copyOf(entries));
        }

        List<Expression> elements = new ArrayList<>();
        elements.add(first);
        while (matchText(",") && !checkText("}")) elements.add(parseExpression());
        consumeText("}", "Se esperaba '}'");
        return new SetExpr(List.copyOf(elements));
    }

    private boolean isAssignmentOperator(String text) { return "=".equals(text); }

    private boolean isAugmentedOperator(String text) {
        return switch (text) {
            case "+=", "-=", "*=", "/=", "//=", "%=", "**=", "&=", "|=", "^=", "<<=", ">>=", "@=" -> true;
            default -> false;
        };
    }

    private Expression requireTarget(Expression expression) {
        validateTarget(expression);
        return expression;
    }

    private void validateTargets(List<Expression> targets) {
        for (Expression target : targets) validateTarget(target);
    }

    private void validateTarget(Expression expression) {
        if (!(expression instanceof Name) && !(expression instanceof Subscript) &&
                !(expression instanceof TupleExpr) && !(expression instanceof ListExpr)) {
            error(peek(), "Objetivo de asignación inválido");
        }
    }

    private boolean matchKeyword(String keyword) {
        if (checkKeyword(keyword)) {
            advance();
            return true;
        }
        return false;
    }

    private boolean checkKeyword(String keyword) {
        return (check(MCodeToken.Type.KEYWORD) || check(MCodeToken.Type.IDENTIFIER)) && keyword.equals(peek().text());
    }

    private boolean matchText(String text) {
        if (checkText(text)) {
            advance();
            return true;
        }
        return false;
    }

    private boolean checkText(String text) { return text.equals(peek().text()); }

    private boolean check(MCodeToken.Type type) { return peek().type() == type; }

    private MCodeToken advance() {
        if (position < tokens.size()) return tokens.get(position++);
        return tokens.get(tokens.size() - 1);
    }

    private MCodeToken previous() { return tokens.get(Math.max(0, position - 1)); }
    private MCodeToken peek() { return tokens.get(Math.min(position, tokens.size() - 1)); }

    private void skipNewlines() { while (match(MCodeToken.Type.NEWLINE)) {} }

    private boolean match(MCodeToken.Type type) {
        if (check(type)) {
            advance();
            return true;
        }
        return false;
    }

    private void consumeText(String text, String message) {
        if (!matchText(text)) error(peek(), message);
    }

    private void error(MCodeToken token, String message) {
        throw new ParseException(message, token.line(), token.column());
    }

    private static Object parseNumber(MCodeToken token) {
        String raw = token.text().replace("_", "");
        try {
            if (raw.startsWith("0x") || raw.startsWith("0X")) return new BigInteger(raw.substring(2), 16);
            if (raw.startsWith("0b") || raw.startsWith("0B")) return new BigInteger(raw.substring(2), 2);
            if (raw.startsWith("0o") || raw.startsWith("0O")) return new BigInteger(raw.substring(2), 8);
            if (raw.contains(".") || raw.contains("e") || raw.contains("E")) return Double.parseDouble(raw);
            return new BigInteger(raw);
        } catch (NumberFormatException e) {
            throw new ParseException("Número inválido: " + token.text(), token.line(), token.column());
        }
    }

    private static String parseString(MCodeToken token) {
        String raw = token.text();
        char quote = raw.charAt(0);
        String body = raw.substring(1, raw.length() - 1);
        StringBuilder result = new StringBuilder();
        boolean escaped = false;
        for (int i = 0; i < body.length(); i++) {
            char c = body.charAt(i);
            if (escaped) {
                result.append(switch (c) {
                    case 'n' -> '\n';
                    case 'r' -> '\r';
                    case 't' -> '\t';
                    case 'b' -> '\b';
                    case 'f' -> '\f';
                    case '\\' -> '\\';
                    case '\'' -> '\'';
                    case '"' -> '"';
                    default -> c;
                });
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else {
                result.append(c);
            }
        }
        if (escaped) result.append('\\');
        return result.toString();
    }

    public static class ParseException extends RuntimeException {
        private final int line;
        private final int column;

        public ParseException(String message, int line, int column) {
            super("Línea " + line + ", columna " + column + ": " + message);
            this.line = line;
            this.column = column;
        }

        public int line() { return line; }
        public int column() { return column; }
    }
}
