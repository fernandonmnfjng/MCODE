package textmenu.interpreter;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MCodeInterpreter {

    private static final Map<String, Object> variables = new LinkedHashMap<>();
    private static final StringBuilder output = new StringBuilder();

    /*
     * ============================================================
     * PUBLIC API
     * ============================================================
     */

    public static String execute(String code) {
        output.setLength(0);

        try {
            List<String> lines = splitLines(code);

            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i).trim();

                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }

                try {
                    executeLine(line);
                } catch (Exception e) {
                    output.append("Error en línea ")
                            .append(i + 1)
                            .append(": ")
                            .append(e.getMessage())
                            .append("\n");
                }
            }

        } catch (Exception e) {
            output.append("Error: ")
                    .append(e.getMessage())
                    .append("\n");
        }

        return output.toString();
    }

    public static boolean validate(String code) {
        return getErrors(code).isEmpty();
    }

    public static List<String> getErrors(String code) {
        List<String> errors = new ArrayList<>();
        List<String> lines = splitLines(code);

        for (int i = 0; i < lines.size(); i++) {
            String line = removeComment(lines.get(i)).trim();

            if (line.isEmpty()) {
                continue;
            }

            try {
                Parser parser = new Parser(line);
                parser.parseStatement();

                if (!parser.isAtEnd()) {
                    throw new RuntimeException("Código inesperado: " + parser.peek().text);
                }
            } catch (Exception e) {
                errors.add("Línea " + (i + 1) + ": " + e.getMessage());
            }
        }

        return errors;
    }

    /*
     * ============================================================
     * LINE EXECUTION
     * ============================================================
     */

    private static void executeLine(String line) {
        line = removeComment(line).trim();

        if (line.isEmpty()) {
            return;
        }

        Parser parser = new Parser(line);
        parser.execute();
    }

    /*
     * ============================================================
     * PARSER
     * ============================================================
     *
     * Grammar:
     *
     * statement
     *     = assignment
     *     | expression
     *
     * assignment
     *     = identifier "=" expression
     *     | identifier "[" expression "]" "=" expression
     *
     * expression
     *     = or
     *
     * or
     *     = and ("or" and)*
     *
     * and
     *     = equality ("and" equality)*
     *
     * equality
     *     = comparison (("==" | "!=") comparison)*
     *
     * comparison
     *     = term (("<" | ">" | "<=" | ">=") term)*
     *
     * term
     *     = factor (("+" | "-") factor)*
     *
     * factor
     *     = unary (("*" | "/" | "//" | "%") unary)*
     *
     * unary
     *     = ("-" | "+" | "not") unary
     *     | power
     *
     * power
     *     = primary ("**" unary)?
     *
     * primary
     *     = number
     *     | string
     *     | list
     *     | tuple
     *     | set
     *     | dict
     *     | identifier
     *     | function call
     *     | "(" expression ")"
     *     | primary "[" expression "]"
     */

    private static class Parser {

        private final List<Token> tokens;
        private int position = 0;

        Parser(String source) {
            this.tokens = tokenize(source);
        }

        void execute() {
            if (isAssignment()) {
                parseAssignment();
            } else {
                Object result = parseExpression();
                if (!isAtEnd()) {
                    throw error("Código inesperado: " + peek().text);
                }
            }
        }

        void parseStatement() {
            if (isAssignment()) {
                parseAssignment();
            } else {
                parseExpression();
            }
        }

        private boolean isAssignment() {
            int depth = 0;

            for (int i = position; i < tokens.size(); i++) {
                Token token = tokens.get(i);

                if (token.type == TokenType.LPAREN ||
                        token.type == TokenType.LBRACKET ||
                        token.type == TokenType.LBRACE) {
                    depth++;
                }

                if (token.type == TokenType.RPAREN ||
                        token.type == TokenType.RBRACKET ||
                        token.type == TokenType.RBRACE) {
                    depth--;
                }

                if (depth == 0 && token.type == TokenType.EQUAL) {
                    return true;
                }
            }

            return false;
        }

        private void parseAssignment() {
            Token name = consume(TokenType.IDENTIFIER, "Se esperaba un nombre de variable");

            if (match(TokenType.LBRACKET)) {
                Object index = parseExpression();
                consume(TokenType.RBRACKET, "Falta ']'");

                consume(TokenType.EQUAL, "Se esperaba '='");

                Object value = parseExpression();

                setIndex(getVariable(name.text), index, value);
                return;
            }

            consume(TokenType.EQUAL, "Se esperaba '='");

            Object value = parseExpression();
            variables.put(name.text, value);

            if (!isAtEnd()) {
                throw error("Código inesperado: " + peek().text);
            }
        }

        private Object parseExpression() {
            return parseOr();
        }

        private Object parseOr() {
            Object left = parseAnd();

            while (matchKeyword("or")) {
                Object right = parseAnd();
                left = truthy(left) ? left : right;
            }

            return left;
        }

        private Object parseAnd() {
            Object left = parseEquality();

            while (matchKeyword("and")) {
                Object right = parseEquality();
                left = truthy(left) ? right : left;
            }

            return left;
        }

        private Object parseEquality() {
            Object left = parseComparison();

            while (true) {
                if (match(TokenType.EQUAL_EQUAL)) {
                    Object right = parseComparison();
                    left = equalsValue(left, right);
                } else if (match(TokenType.NOT_EQUAL)) {
                    Object right = parseComparison();
                    left = !equalsValue(left, right);
                } else {
                    break;
                }
            }

            return left;
        }

        private Object parseComparison() {
            Object left = parseTerm();

            while (true) {
                if (match(TokenType.LESS)) {
                    Object right = parseTerm();
                    left = compare(left, right) < 0;
                } else if (match(TokenType.LESS_EQUAL)) {
                    Object right = parseTerm();
                    left = compare(left, right) <= 0;
                } else if (match(TokenType.GREATER)) {
                    Object right = parseTerm();
                    left = compare(left, right) > 0;
                } else if (match(TokenType.GREATER_EQUAL)) {
                    Object right = parseTerm();
                    left = compare(left, right) >= 0;
                } else {
                    break;
                }
            }

            return left;
        }

        private Object parseTerm() {
            Object left = parseFactor();

            while (true) {
                if (match(TokenType.PLUS)) {
                    Object right = parseFactor();
                    left = add(left, right);
                } else if (match(TokenType.MINUS)) {
                    Object right = parseFactor();
                    left = subtract(left, right);
                } else {
                    break;
                }
            }

            return left;
        }

        private Object parseFactor() {
            Object left = parseUnary();

            while (true) {
                if (match(TokenType.STAR)) {
                    Object right = parseUnary();
                    left = multiply(left, right);
                } else if (match(TokenType.SLASH)) {
                    Object right = parseUnary();
                    left = divide(left, right);
                } else if (match(TokenType.DOUBLE_SLASH)) {
                    Object right = parseUnary();
                    left = floorDivide(left, right);
                } else if (match(TokenType.PERCENT)) {
                    Object right = parseUnary();
                    left = modulo(left, right);
                } else {
                    break;
                }
            }

            return left;
        }

        private Object parseUnary() {
            if (match(TokenType.MINUS)) {
                return negate(parseUnary());
            }

            if (match(TokenType.PLUS)) {
                return positive(parseUnary());
            }

            if (matchKeyword("not")) {
                return !truthy(parseUnary());
            }

            return parsePower();
        }

        private Object parsePower() {
            Object left = parsePrimary();

            if (match(TokenType.DOUBLE_STAR)) {
                Object right = parseUnary();
                return power(left, right);
            }

            return left;
        }

        private Object parsePrimary() {
            Token token = peek();

            if (match(TokenType.INTEGER)) {
                return new BigInteger(token.text);
            }

            if (match(TokenType.FLOAT)) {
                return Double.parseDouble(token.text);
            }

            if (match(TokenType.STRING)) {
                return token.text;
            }

            if (matchKeyword("true")) {
                return true;
            }

            if (matchKeyword("false")) {
                return false;
            }

            if (matchKeyword("None")) {
                return null;
            }

            if (matchKeyword("True")) {
                return true;
            }

            if (matchKeyword("False")) {
                return false;
            }

            if (match(TokenType.LBRACKET)) {
                return parseList();
            }

            if (match(TokenType.LPAREN)) {
                Object value = parseExpression();

                if (match(TokenType.COMMA)) {
                    List<Object> tuple = new ArrayList<>();
                    tuple.add(value);

                    while (!check(TokenType.RPAREN)) {
                        tuple.add(parseExpression());

                        if (!match(TokenType.COMMA)) {
                            break;
                        }
                    }

                    consume(TokenType.RPAREN, "Falta ')'");

                    return new TupleValue(tuple);
                }

                consume(TokenType.RPAREN, "Falta ')'");

                return value;
            }

            if (match(TokenType.LBRACE)) {
                return parseBraceValue();
            }

            if (match(TokenType.IDENTIFIER)) {
                String name = token.text;

                if (match(TokenType.LPAREN)) {
                    return callFunction(name);
                }

                Object value = getVariable(name);

                while (match(TokenType.LBRACKET)) {
                    Object index = parseExpression();
                    consume(TokenType.RBRACKET, "Falta ']'");
                    value = getIndex(value, index);
                }

                return value;
            }

            throw error("Se esperaba un valor, pero apareció: " + token.text);
        }

        private List<Object> parseList() {
            List<Object> list = new ArrayList<>();

            if (!check(TokenType.RBRACKET)) {
                do {
                    list.add(parseExpression());
                } while (match(TokenType.COMMA));
            }

            consume(TokenType.RBRACKET, "Falta ']'");

            return list;
        }

        private Object parseBraceValue() {
            if (check(TokenType.RBRACE)) {
                advance();
                return new LinkedHashMap<String, Object>();
            }

            Object first = parseExpression();

            if (match(TokenType.COLON)) {
                Map<Object, Object> map = new LinkedHashMap<>();

                Object value = parseExpression();
                map.put(first, value);

                while (match(TokenType.COMMA)) {
                    if (check(TokenType.RBRACE)) {
                        break;
                    }

                    Object key = parseExpression();
                    consume(TokenType.COLON, "Falta ':' en diccionario");
                    Object val = parseExpression();

                    map.put(key, val);
                }

                consume(TokenType.RBRACE, "Falta '}'");

                return map;
            }

            Set<Object> set = new LinkedHashSet<>();
            set.add(first);

            while (match(TokenType.COMMA)) {
                if (check(TokenType.RBRACE)) {
                    break;
                }

                set.add(parseExpression());
            }

            consume(TokenType.RBRACE, "Falta '}'");

            return set;
        }

        private Object callFunction(String name) {
            List<Object> args = new ArrayList<>();

            if (!check(TokenType.RPAREN)) {
                do {
                    args.add(parseExpression());
                } while (match(TokenType.COMMA));
            }

            consume(TokenType.RPAREN, "Falta ')'");

            return callBuiltin(name, args);
        }

        private boolean matchKeyword(String keyword) {
            if (check(TokenType.IDENTIFIER) && peek().text.equals(keyword)) {
                advance();
                return true;
            }

            return false;
        }

        private boolean match(TokenType type) {
            if (check(type)) {
                advance();
                return true;
            }

            return false;
        }

        private Token consume(TokenType type, String message) {
            if (check(type)) {
                return advance();
            }

            throw error(message);
        }

        private boolean check(TokenType type) {
            return peek().type == type;
        }

        private Token advance() {
            if (!isAtEnd()) {
                position++;
            }

            return previous();
        }

        private boolean isAtEnd() {
            return peek().type == TokenType.EOF;
        }

        private Token peek() {
            return tokens.get(position);
        }

        private Token previous() {
            return tokens.get(position - 1);
        }

        private RuntimeException error(String message) {
            return new RuntimeException(message);
        }
    }

    /*
     * ============================================================
     * BUILT-IN FUNCTIONS
     * ============================================================
     */

    private static Object callBuiltin(String name, List<Object> args) {
        switch (name) {

            case "print":
                for (int i = 0; i < args.size(); i++) {
                    if (i > 0) {
                        output.append(" ");
                    }

                    output.append(stringify(args.get(i)));
                }

                output.append("\n");
                return null;

            case "len":
                requireArgs(name, args, 1);

                Object value = args.get(0);

                if (value instanceof String s) {
                    return BigInteger.valueOf(s.length());
                }

                if (value instanceof List<?> list) {
                    return BigInteger.valueOf(list.size());
                }

                if (value instanceof TupleValue tuple) {
                    return BigInteger.valueOf(tuple.values.size());
                }

                if (value instanceof Set<?> set) {
                    return BigInteger.valueOf(set.size());
                }

                if (value instanceof Map<?, ?> map) {
                    return BigInteger.valueOf(map.size());
                }

                if (value instanceof byte[] bytes) {
                    return BigInteger.valueOf(bytes.length);
                }

                throw new RuntimeException("len() no soporta " + typeName(value));

            case "type":
                requireArgs(name, args, 1);
                return typeName(args.get(0));

            case "int":
                requireArgs(name, args, 1);
                return toInteger(args.get(0));

            case "float":
                requireArgs(name, args, 1);
                return toDouble(args.get(0));

            case "str":
                requireArgs(name, args, 1);
                return stringify(args.get(0));

            case "bool":
                requireArgs(name, args, 1);
                return truthy(args.get(0));

            case "list":
                requireArgs(name, args, 1);

                if (args.get(0) instanceof List<?> list) {
                    return new ArrayList<>(list);
                }

                if (args.get(0) instanceof TupleValue tuple) {
                    return new ArrayList<>(tuple.values);
                }

                throw new RuntimeException("list() necesita una lista o tupla");

            case "tuple":
                requireArgs(name, args, 1);

                if (args.get(0) instanceof List<?> list) {
                    return new TupleValue(new ArrayList<>(list));
                }

                if (args.get(0) instanceof TupleValue tuple) {
                    return new TupleValue(new ArrayList<>(tuple.values));
                }

                throw new RuntimeException("tuple() necesita una lista o tupla");

            case "set":
                requireArgs(name, args, 1);

                if (args.get(0) instanceof List<?> list) {
                    return new LinkedHashSet<>(list);
                }

                if (args.get(0) instanceof TupleValue tuple) {
                    return new LinkedHashSet<>(tuple.values);
                }

                throw new RuntimeException("set() necesita una lista o tupla");

            case "abs":
                requireArgs(name, args, 1);

                if (args.get(0) instanceof BigInteger integer) {
                    return integer.abs();
                }

                return Math.abs(toDouble(args.get(0)));

            case "round":
                requireArgs(name, args, 1);
                return BigInteger.valueOf(Math.round(toDouble(args.get(0))));

            case "min":
                requireAtLeast(name, args, 1);

                Object minimum = args.get(0);

                for (Object arg : args) {
                    if (compare(arg, minimum) < 0) {
                        minimum = arg;
                    }
                }

                return minimum;

            case "max":
                requireAtLeast(name, args, 1);

                Object maximum = args.get(0);

                for (Object arg : args) {
                    if (compare(arg, maximum) > 0) {
                        maximum = arg;
                    }
                }

                return maximum;

            default:
                throw new RuntimeException("Función desconocida: " + name);
        }
    }

    /*
     * ============================================================
     * OPERATIONS
     * ============================================================
     */

    private static Object add(Object a, Object b) {

        if (a instanceof String || b instanceof String) {
            return stringify(a) + stringify(b);
        }

        if (a instanceof List<?> listA && b instanceof List<?> listB) {
            List<Object> result = new ArrayList<>(listA);
            result.addAll(listB);
            return result;
        }

        if (a instanceof BigInteger ia && b instanceof BigInteger ib) {
            return ia.add(ib);
        }

        if (isNumber(a) && isNumber(b)) {
            return toDouble(a) + toDouble(b);
        }

        throw new RuntimeException(
                "No se puede sumar " + typeName(a) + " + " + typeName(b)
        );
    }

    private static Object subtract(Object a, Object b) {

        if (a instanceof BigInteger ia && b instanceof BigInteger ib) {
            return ia.subtract(ib);
        }

        if (isNumber(a) && isNumber(b)) {
            return toDouble(a) - toDouble(b);
        }

        throw new RuntimeException(
                "No se puede restar " + typeName(a) + " - " + typeName(b)
        );
    }

    private static Object multiply(Object a, Object b) {

        if (a instanceof BigInteger ia && b instanceof BigInteger ib) {
            return ia.multiply(ib);
        }

        if (isNumber(a) && isNumber(b)) {
            return toDouble(a) * toDouble(b);
        }

        if (a instanceof String s && b instanceof BigInteger n) {
            return repeatString(s, n.intValue());
        }

        if (b instanceof String s && a instanceof BigInteger n) {
            return repeatString(s, n.intValue());
        }

        if (a instanceof List<?> list && b instanceof BigInteger n) {
            List<Object> result = new ArrayList<>();

            for (int i = 0; i < n.intValue(); i++) {
                result.addAll(list);
            }

            return result;
        }

        throw new RuntimeException(
                "No se puede multiplicar " + typeName(a) + " * " + typeName(b)
        );
    }

    private static Object divide(Object a, Object b) {
        double divisor = toDouble(b);

        if (divisor == 0) {
            throw new RuntimeException("División por cero");
        }

        return toDouble(a) / divisor;
    }

    private static Object floorDivide(Object a, Object b) {
        double divisor = toDouble(b);

        if (divisor == 0) {
            throw new RuntimeException("División por cero");
        }

        return Math.floor(toDouble(a) / divisor);
    }

    private static Object modulo(Object a, Object b) {

        if (a instanceof BigInteger ia && b instanceof BigInteger ib) {
            return ia.mod(ib);
        }

        return toDouble(a) % toDouble(b);
    }

    private static Object power(Object a, Object b) {
        if (a instanceof BigInteger ia && b instanceof BigInteger ib && ib.signum() >= 0) {
            try {
                return ia.pow(ib.intValueExact());
            } catch (ArithmeticException ignored) {
            }
        }

        return Math.pow(toDouble(a), toDouble(b));
    }

    private static Object negate(Object value) {
        if (value instanceof BigInteger integer) {
            return integer.negate();
        }

        if (value instanceof Number number) {
            return -number.doubleValue();
        }

        throw new RuntimeException("No se puede negar " + typeName(value));
    }

    private static Object positive(Object value) {
        if (isNumber(value)) {
            return value;
        }

        throw new RuntimeException("No se puede aplicar + a " + typeName(value));
    }

    /*
     * ============================================================
     * INDEXING
     * ============================================================
     */

    private static Object getIndex(Object object, Object index) {

        int i = toInteger(index).intValue();

        if (object instanceof String string) {
            if (i < 0) {
                i += string.length();
            }

            if (i < 0 || i >= string.length()) {
                throw new RuntimeException("Índice fuera de rango");
            }

            return String.valueOf(string.charAt(i));
        }

        if (object instanceof List<?> list) {
            if (i < 0) {
                i += list.size();
            }

            if (i < 0 || i >= list.size()) {
                throw new RuntimeException("Índice fuera de rango");
            }

            return list.get(i);
        }

        if (object instanceof TupleValue tuple) {
            if (i < 0) {
                i += tuple.values.size();
            }

            if (i < 0 || i >= tuple.values.size()) {
                throw new RuntimeException("Índice fuera de rango");
            }

            return tuple.values.get(i);
        }

        if (object instanceof Map<?, ?> map) {
            return map.get(index);
        }

        throw new RuntimeException(
                typeName(object) + " no permite índices"
        );
    }

    @SuppressWarnings("unchecked")
    private static void setIndex(Object object, Object index, Object value) {

        int i = toInteger(index).intValue();

        if (object instanceof List<?> list) {
            List<Object> mutable = (List<Object>) list;

            if (i < 0) {
                i += mutable.size();
            }

            if (i < 0 || i >= mutable.size()) {
                throw new RuntimeException("Índice fuera de rango");
            }

            mutable.set(i, value);
            return;
        }

        if (object instanceof Map<?, ?> map) {
            ((Map<Object, Object>) map).put(index, value);
            return;
        }

        throw new RuntimeException(
                typeName(object) + " no permite asignación por índice"
        );
    }

    /*
     * ============================================================
     * VALUES / TYPES
     * ============================================================
     */

    private static boolean isNumber(Object value) {
        return value instanceof BigInteger ||
                value instanceof Double ||
                value instanceof Float ||
                value instanceof Integer ||
                value instanceof Long ||
                value instanceof Short ||
                value instanceof Byte;
    }

    private static BigInteger toInteger(Object value) {

        if (value instanceof BigInteger integer) {
            return integer;
        }

        if (value instanceof Number number) {
            return BigInteger.valueOf(number.longValue());
        }

        if (value instanceof String string) {
            try {
                return new BigInteger(string);
            } catch (NumberFormatException e) {
                throw new RuntimeException("No es un entero válido: " + string);
            }
        }

        if (value instanceof Boolean bool) {
            return bool ? BigInteger.ONE : BigInteger.ZERO;
        }

        throw new RuntimeException(
                "No se puede convertir " + typeName(value) + " a int"
        );
    }

    private static double toDouble(Object value) {

        if (value instanceof Number number) {
            return number.doubleValue();
        }

        if (value instanceof String string) {
            try {
                return Double.parseDouble(string);
            } catch (NumberFormatException e) {
                throw new RuntimeException("No es un número válido: " + string);
            }
        }

        if (value instanceof Boolean bool) {
            return bool ? 1.0 : 0.0;
        }

        throw new RuntimeException(
                "No se puede convertir " + typeName(value) + " a float"
        );
    }

    private static boolean truthy(Object value) {

        if (value == null) {
            return false;
        }

        if (value instanceof Boolean bool) {
            return bool;
        }

        if (value instanceof BigInteger integer) {
            return integer.signum() != 0;
        }

        if (value instanceof Number number) {
            return number.doubleValue() != 0;
        }

        if (value instanceof String string) {
            return !string.isEmpty();
        }

        if (value instanceof List<?> list) {
            return !list.isEmpty();
        }

        if (value instanceof TupleValue tuple) {
            return !tuple.values.isEmpty();
        }

        if (value instanceof Set<?> set) {
            return !set.isEmpty();
        }

        if (value instanceof Map<?, ?> map) {
            return !map.isEmpty();
        }

        return true;
    }

    private static boolean equalsValue(Object a, Object b) {

        if (a == null && b == null) {
            return true;
        }

        if (a == null || b == null) {
            return false;
        }

        if (isNumber(a) && isNumber(b)) {
            return Double.compare(toDouble(a), toDouble(b)) == 0;
        }

        return a.equals(b);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static int compare(Object a, Object b) {

        if (isNumber(a) && isNumber(b)) {
            return Double.compare(toDouble(a), toDouble(b));
        }

        if (a instanceof String sa && b instanceof String sb) {
            return sa.compareTo(sb);
        }

        if (a instanceof Comparable ca && a.getClass().isInstance(b)) {
            return ca.compareTo(b);
        }

        throw new RuntimeException(
                "No se pueden comparar " +
                        typeName(a) +
                        " y " +
                        typeName(b)
        );
    }

    private static String typeName(Object value) {

        if (value == null) return "None";
        if (value instanceof Boolean) return "bool";
        if (value instanceof BigInteger) return "int";
        if (value instanceof Double) return "float";
        if (value instanceof String) return "str";
        if (value instanceof List<?>) return "list";
        if (value instanceof TupleValue) return "tuple";
        if (value instanceof Set<?>) return "set";
        if (value instanceof Map<?, ?>) return "dict";
        if (value instanceof byte[]) return "bytes";
        if (value instanceof ComplexValue) return "complex";

        return value.getClass().getSimpleName();
    }

    private static String stringify(Object value) {

        if (value == null) {
            return "None";
        }

        if (value instanceof Boolean bool) {
            return bool ? "True" : "False";
        }

        if (value instanceof BigInteger integer) {
            return integer.toString();
        }

        if (value instanceof Double number) {
            if (number.isNaN()) return "nan";
            if (number.isInfinite()) {
                return number > 0 ? "inf" : "-inf";
            }

            if (number == Math.rint(number)) {
                return String.format("%.1f", number);
            }

            return number.toString();
        }

        if (value instanceof String string) {
            return string;
        }

        if (value instanceof List<?> list) {
            return collectionToString(list, "[", "]");
        }

        if (value instanceof TupleValue tuple) {
            return collectionToString(tuple.values, "(", ")");
        }

        if (value instanceof Set<?> set) {
            return collectionToString(set, "{", "}");
        }

        if (value instanceof Map<?, ?> map) {
            StringBuilder result = new StringBuilder("{");

            boolean first = true;

            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!first) {
                    result.append(", ");
                }

                first = false;

                result.append(stringify(entry.getKey()))
                        .append(": ")
                        .append(stringify(entry.getValue()));
            }

            result.append("}");

            return result.toString();
        }

        if (value instanceof ComplexValue complex) {
            return complex.toString();
        }

        if (value instanceof byte[] bytes) {
            StringBuilder result = new StringBuilder("b'");

            for (byte b : bytes) {
                result.append((char) (b & 0xFF));
            }

            result.append("'");

            return result.toString();
        }

        return String.valueOf(value);
    }

    private static String collectionToString(
            Iterable<?> values,
            String start,
            String end
    ) {
        StringBuilder result = new StringBuilder(start);

        boolean first = true;

        for (Object value : values) {
            if (!first) {
                result.append(", ");
            }

            first = false;
            result.append(stringify(value));
        }

        result.append(end);

        return result.toString();
    }

    /*
     * ============================================================
     * SPECIAL TYPES
     * ============================================================
     */

    private static class TupleValue {

        final List<Object> values;

        TupleValue(List<Object> values) {
            this.values = values;
        }

        @Override
        public boolean equals(Object object) {
            if (!(object instanceof TupleValue other)) {
                return false;
            }

            return values.equals(other.values);
        }

        @Override
        public int hashCode() {
            return values.hashCode();
        }
    }

    private static class ComplexValue {

        final double real;
        final double imaginary;

        ComplexValue(double real, double imaginary) {
            this.real = real;
            this.imaginary = imaginary;
        }

        @Override
        public String toString() {
            if (real == 0) {
                return imaginary + "j";
            }

            return real + (imaginary >= 0 ? "+" : "") + imaginary + "j";
        }
    }

    /*
     * ============================================================
     * TOKENIZER
     * ============================================================
     */

    private enum TokenType {

        INTEGER,
        FLOAT,
        STRING,
        IDENTIFIER,

        PLUS,
        MINUS,
        STAR,
        DOUBLE_STAR,
        SLASH,
        DOUBLE_SLASH,
        PERCENT,

        EQUAL,
        EQUAL_EQUAL,
        NOT_EQUAL,

        LESS,
        LESS_EQUAL,
        GREATER,
        GREATER_EQUAL,

        LPAREN,
        RPAREN,
        LBRACKET,
        RBRACKET,
        LBRACE,
        RBRACE,

        COMMA,
        COLON,

        EOF
    }

    private static class Token {

        final TokenType type;
        final String text;

        Token(TokenType type, String text) {
            this.type = type;
            this.text = text;
        }
    }

    private static List<Token> tokenize(String source) {

        List<Token> tokens = new ArrayList<>();

        int i = 0;

        while (i < source.length()) {

            char c = source.charAt(i);

            if (Character.isWhitespace(c)) {
                i++;
                continue;
            }

            if (c == '#') {
                break;
            }

            /*
             * Numbers
             */

            if (Character.isDigit(c) ||
                    (c == '.' && i + 1 < source.length() &&
                            Character.isDigit(source.charAt(i + 1)))) {

                int start = i;
                boolean hasDot = false;

                if (c == '.') {
                    hasDot = true;
                    i++;
                }

                while (i < source.length() &&
                        Character.isDigit(source.charAt(i))) {
                    i++;
                }

                if (i < source.length() && source.charAt(i) == '.') {
                    hasDot = true;
                    i++;

                    while (i < source.length() &&
                            Character.isDigit(source.charAt(i))) {
                        i++;
                    }
                }

                String number = source.substring(start, i);

                tokens.add(new Token(
                        hasDot ? TokenType.FLOAT : TokenType.INTEGER,
                        number
                ));

                continue;
            }

            /*
             * Strings
             */

            if (c == '"' || c == '\'') {

                char quote = c;
                i++;

                StringBuilder string = new StringBuilder();

                while (i < source.length() && source.charAt(i) != quote) {

                    char current = source.charAt(i);

                    if (current == '\\' && i + 1 < source.length()) {

                        i++;

                        char escaped = source.charAt(i);

                        switch (escaped) {
                            case 'n' -> string.append('\n');
                            case 't' -> string.append('\t');
                            case 'r' -> string.append('\r');
                            case '\\' -> string.append('\\');
                            case '"' -> string.append('"');
                            case '\'' -> string.append('\'');
                            default -> string.append(escaped);
                        }

                        i++;
                        continue;
                    }

                    string.append(current);
                    i++;
                }

                if (i >= source.length()) {
                    throw new RuntimeException("String sin cerrar");
                }

                i++;

                tokens.add(new Token(
                        TokenType.STRING,
                        string.toString()
                ));

                continue;
            }

            /*
             * Identifiers
             */

            if (Character.isLetter(c) || c == '_') {

                int start = i;
                i++;

                while (i < source.length()) {

                    char current = source.charAt(i);

                    if (Character.isLetterOrDigit(current) || current == '_') {
                        i++;
                    } else {
                        break;
                    }
                }

                tokens.add(new Token(
                        TokenType.IDENTIFIER,
                        source.substring(start, i)
                ));

                continue;
            }

            /*
             * Operators
             */

            if (c == '*' && i + 1 < source.length() &&
                    source.charAt(i + 1) == '*') {

                tokens.add(new Token(TokenType.DOUBLE_STAR, "**"));
                i += 2;
                continue;
            }

            if (c == '/' && i + 1 < source.length() &&
                    source.charAt(i + 1) == '/') {

                tokens.add(new Token(TokenType.DOUBLE_SLASH, "//"));
                i += 2;
                continue;
            }

            if (c == '=' && i + 1 < source.length() &&
                    source.charAt(i + 1) == '=') {

                tokens.add(new Token(TokenType.EQUAL_EQUAL, "=="));
                i += 2;
                continue;
            }

            if (c == '!' && i + 1 < source.length() &&
                    source.charAt(i + 1) == '=') {

                tokens.add(new Token(TokenType.NOT_EQUAL, "!="));
                i += 2;
                continue;
            }

            if (c == '<' && i + 1 < source.length() &&
                    source.charAt(i + 1) == '=') {

                tokens.add(new Token(TokenType.LESS_EQUAL, "<="));
                i += 2;
                continue;
            }

            if (c == '>' && i + 1 < source.length() &&
                    source.charAt(i + 1) == '=') {

                tokens.add(new Token(TokenType.GREATER_EQUAL, ">="));
                i += 2;
                continue;
            }

            switch (c) {

                case '+' -> tokens.add(new Token(TokenType.PLUS, "+"));
                case '-' -> tokens.add(new Token(TokenType.MINUS, "-"));
                case '*' -> tokens.add(new Token(TokenType.STAR, "*"));
                case '/' -> tokens.add(new Token(TokenType.SLASH, "/"));
                case '%' -> tokens.add(new Token(TokenType.PERCENT, "%"));

                case '=' -> tokens.add(new Token(TokenType.EQUAL, "="));

                case '<' -> tokens.add(new Token(TokenType.LESS, "<"));
                case '>' -> tokens.add(new Token(TokenType.GREATER, ">"));

                case '(' -> tokens.add(new Token(TokenType.LPAREN, "("));
                case ')' -> tokens.add(new Token(TokenType.RPAREN, ")"));

                case '[' -> tokens.add(new Token(TokenType.LBRACKET, "["));
                case ']' -> tokens.add(new Token(TokenType.RBRACKET, "]"));

                case '{' -> tokens.add(new Token(TokenType.LBRACE, "{"));
                case '}' -> tokens.add(new Token(TokenType.RBRACE, "}"));

                case ',' -> tokens.add(new Token(TokenType.COMMA, ","));
                case ':' -> tokens.add(new Token(TokenType.COLON, ":"));

                default -> throw new RuntimeException(
                        "Carácter no reconocido: '" + c + "'"
                );
            }

            i++;
        }

        tokens.add(new Token(TokenType.EOF, ""));

        return tokens;
    }

    /*
     * ============================================================
     * UTILITIES
     * ============================================================
     */

    private static List<String> splitLines(String code) {
        return List.of(code.split("\\R", -1));
    }

    private static String removeComment(String line) {

        boolean inString = false;
        char quote = 0;

        for (int i = 0; i < line.length(); i++) {

            char c = line.charAt(i);

            if ((c == '"' || c == '\'') &&
                    (i == 0 || line.charAt(i - 1) != '\\')) {

                if (!inString) {
                    inString = true;
                    quote = c;
                } else if (quote == c) {
                    inString = false;
                }

                continue;
            }

            if (c == '#' && !inString) {
                return line.substring(0, i);
            }
        }

        return line;
    }

    private static String repeatString(String string, int times) {

        if (times < 0) {
            throw new RuntimeException(
                    "No se puede repetir una cadena un número negativo de veces"
            );
        }

        StringBuilder result = new StringBuilder();

        for (int i = 0; i < times; i++) {
            result.append(string);
        }

        return result.toString();
    }

    private static void requireArgs(
            String function,
            List<Object> args,
            int amount
    ) {
        if (args.size() != amount) {
            throw new RuntimeException(
                    function + "() necesita " + amount + " argumento(s)"
            );
        }
    }

    private static void requireAtLeast(
            String function,
            List<Object> args,
            int amount
    ) {
        if (args.size() < amount) {
            throw new RuntimeException(
                    function + "() necesita al menos " + amount + " argumento(s)"
            );
        }
    }

    private static Object getVariable(String name) {

        if (!variables.containsKey(name)) {
            throw new RuntimeException(
                    "Variable no definida: " + name
            );
        }

        return variables.get(name);
    }
}