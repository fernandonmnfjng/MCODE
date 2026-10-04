package textmenu.interpreter;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static textmenu.interpreter.MCodeAst.*;

public final class MCodeInterpreter {
    private static final Object NONE = null;
    private static final StringBuilder OUTPUT = new StringBuilder();
    private static Map<String, Object> lastVariables = new LinkedHashMap<>();

    private MCodeInterpreter() {}

    public static String execute(String code) {
        OUTPUT.setLength(0);
        try {
            List<MCodeToken> tokens = new MCodeLexer().tokenize(code);
            Program program = new MCodeParser(tokens).parse();
            Runtime runtime = new Runtime();
            runtime.installBuiltins();
            for (Statement statement : program.statements()) runtime.execute(statement);
            lastVariables = runtime.variablesSnapshot();
            return OUTPUT.toString();
        } catch (RuntimeException e) {
            return formatError(e);
        }
    }

    public static boolean validate(String code) {
        try {
            new MCodeParser(new MCodeLexer().tokenize(code)).parse();
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    public static List<String> getErrors(String code) {
        try {
            new MCodeParser(new MCodeLexer().tokenize(code)).parse();
            return List.of();
        } catch (RuntimeException e) {
            return List.of(e.getMessage() == null ? e.toString() : e.getMessage());
        }
    }

    public static Map<String, Object> getVariables() {
        return Collections.unmodifiableMap(lastVariables);
    }

    public static List<String> getLastOutputLines() {
        if (OUTPUT.isEmpty()) return List.of();
        String normalized = OUTPUT.toString().replace("\r\n", "\n").replace('\r', '\n');
        String[] parts = normalized.split("\n", -1);
        int length = parts.length;
        if (length > 0 && parts[length - 1].isEmpty()) length--;
        return List.of(java.util.Arrays.copyOf(parts, length));
    }

    public static void clearVariables() {
        lastVariables = new LinkedHashMap<>();
    }

    private static String formatError(RuntimeException e) {
        OUTPUT.append("Error: ").append(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()).append('\n');
        return OUTPUT.toString();
    }

    private static final class Runtime {
        private final Map<String, Object> variables = new LinkedHashMap<>();

        void installBuiltins() {
            variables.put("print", (Callable) this::builtinPrint);
            variables.put("len", (Callable) (args, keywords) -> {
                requireArgs("len", args, 1);
                return length(args.get(0));
            });
            variables.put("type", (Callable) (args, keywords) -> {
                requireArgs("type", args, 1);
                return typeName(args.get(0));
            });
            variables.put("int", (Callable) (args, keywords) -> convertInt(args.isEmpty() ? BigInteger.ZERO : args.get(0)));
            variables.put("float", (Callable) (args, keywords) -> convertFloat(args.isEmpty() ? 0.0 : args.get(0)));
            variables.put("str", (Callable) (args, keywords) -> args.isEmpty() ? "" : stringify(args.get(0)));
            variables.put("bool", (Callable) (args, keywords) -> truthy(args.isEmpty() ? null : args.get(0)));
            variables.put("list", (Callable) (args, keywords) -> toList(args.isEmpty() ? null : args.get(0)));
            variables.put("tuple", (Callable) (args, keywords) -> new TupleValue(toList(args.isEmpty() ? null : args.get(0))));
            variables.put("set", (Callable) (args, keywords) -> new LinkedHashSet<>(toList(args.isEmpty() ? null : args.get(0))));
            variables.put("dict", (Callable) (args, keywords) -> new LinkedHashMap<>());
            variables.put("abs", (Callable) (args, keywords) -> {
                requireArgs("abs", args, 1);
                Object value = args.get(0);
                if (value instanceof BigInteger i) return i.abs();
                if (value instanceof Number n) return Math.abs(n.doubleValue());
                throw runtimeError("abs() requiere un número");
            });
            variables.put("round", (Callable) (args, keywords) -> {
                requireArgs("round", args, 1);
                Object value = args.get(0);
                if (value instanceof BigInteger) return value;
                if (value instanceof Number n) return BigInteger.valueOf(Math.round(n.doubleValue()));
                throw runtimeError("round() requiere un número");
            });
            variables.put("min", (Callable) (args, keywords) -> minMax(args, true));
            variables.put("max", (Callable) (args, keywords) -> minMax(args, false));
            variables.put("sum", (Callable) (args, keywords) -> {
                requireArgs("sum", args, 1);
                List<Object> values = iterable(args.get(0));
                Object result = BigInteger.ZERO;
                for (Object value : values) result = binary(result, "+", value);
                return result;
            });
            variables.put("any", (Callable) (args, keywords) -> {
                requireArgs("any", args, 1);
                for (Object value : iterable(args.get(0))) if (truthy(value)) return true;
                return false;
            });
            variables.put("all", (Callable) (args, keywords) -> {
                requireArgs("all", args, 1);
                for (Object value : iterable(args.get(0))) if (!truthy(value)) return false;
                return true;
            });
        }

        void execute(Statement statement) {
            if (statement instanceof ExpressionStatement s) {
                eval(s.expression());
            } else if (statement instanceof Assignment s) {
                Object value = eval(s.value());
                for (Expression target : s.targets()) assign(target, value);
            } else if (statement instanceof AugmentedAssignment s) {
                Object current = eval(s.target());
                Object value = eval(s.value());
                assign(s.target(), binary(current, s.operator(), value));
            } else {
                throw runtimeError("Sentencia no soportada todavía: " + statement.getClass().getSimpleName());
            }
        }

        Object eval(Expression expression) {
            if (expression instanceof Literal l) return l.value();
            if (expression instanceof Name n) return lookup(n.name());
            if (expression instanceof ListExpr l) {
                List<Object> result = new ArrayList<>();
                for (Expression item : l.elements()) result.add(eval(item));
                return result;
            }
            if (expression instanceof TupleExpr t) {
                List<Object> result = new ArrayList<>();
                for (Expression item : t.elements()) result.add(eval(item));
                return new TupleValue(result);
            }
            if (expression instanceof SetExpr s) {
                Set<Object> result = new LinkedHashSet<>();
                for (Expression item : s.elements()) result.add(eval(item));
                return result;
            }
            if (expression instanceof DictExpr d) {
                Map<Object, Object> result = new LinkedHashMap<>();
                for (Entry entry : d.entries()) result.put(eval(entry.key()), eval(entry.value()));
                return result;
            }
            if (expression instanceof Unary u) return unary(u.operator(), eval(u.expression()));
            if (expression instanceof Binary b) {
                if (b.operator().equals("and")) {
                    Object left = eval(b.left());
                    return truthy(left) ? eval(b.right()) : left;
                }
                if (b.operator().equals("or")) {
                    Object left = eval(b.left());
                    return truthy(left) ? left : eval(b.right());
                }
                return binary(eval(b.left()), b.operator(), eval(b.right()));
            }
            if (expression instanceof Comparison c) {
                Object left = eval(c.left());
                for (int i = 0; i < c.operators().size(); i++) {
                    Object right = eval(c.rights().get(i));
                    if (!comparison(left, c.operators().get(i), right)) return false;
                    left = right;
                }
                return true;
            }
            if (expression instanceof Call c) {
                Object callable = eval(c.callee());
                if (!(callable instanceof Callable function)) throw runtimeError("'" + typeName(callable) + "' no es invocable");
                List<Object> positional = new ArrayList<>();
                Map<String, Object> keywords = new LinkedHashMap<>();
                for (CallArg arg : c.arguments()) {
                    Object value = eval(arg.value());
                    if (arg.keywordUnpack()) {
                        if (!(value instanceof Map<?, ?> map)) throw runtimeError("** requiere un diccionario");
                        for (Map.Entry<?, ?> entry : map.entrySet()) keywords.put(String.valueOf(entry.getKey()), entry.getValue());
                    } else if (arg.unpack()) {
                        positional.addAll(iterable(value));
                    } else if (arg.name() != null) {
                        keywords.put(arg.name(), value);
                    } else {
                        positional.add(value);
                    }
                }
                return function.call(positional, keywords);
            }
            if (expression instanceof Subscript s) {
                Object target = eval(s.target());
                if (s.subscript() instanceof Slice slice) return readSlice(target, slice);
                return readSubscript(target, eval(s.subscript()));
            }
            if (expression instanceof Slice) throw runtimeError("Una slice debe estar dentro de un subscript");
            throw runtimeError("Expresión no soportada todavía: " + expression.getClass().getSimpleName());
        }

        private Object lookup(String name) {
            if (!variables.containsKey(name)) throw runtimeError("NameError: name '" + name + "' is not defined");
            return variables.get(name);
        }

        private void assign(Expression target, Object value) {
            if (target instanceof Name n) {
                variables.put(n.name(), value);
                return;
            }
            if (target instanceof Subscript s) {
                Object container = eval(s.target());
                Object index = eval(s.subscript());
                writeSubscript(container, index, value);
                return;
            }
            if (target instanceof TupleExpr t) {
                unpackAssign(t.elements(), value);
                return;
            }
            if (target instanceof ListExpr l) {
                unpackAssign(l.elements(), value);
                return;
            }
            throw runtimeError("Objetivo de asignación inválido");
        }

        private void unpackAssign(List<Expression> targets, Object value) {
            List<Object> values = iterable(value);
            if (targets.size() != values.size()) throw runtimeError("ValueError: demasiados o muy pocos valores para desempaquetar");
            for (int i = 0; i < targets.size(); i++) assign(targets.get(i), values.get(i));
        }

        private Object readSubscript(Object target, Object index) {
            if (index instanceof Slice slice) return readSlice(target, slice);
            if (target instanceof Map<?, ?> map) return map.get(index);
            int i = indexValue(index, length(target));
            if (target instanceof String s) return String.valueOf(s.charAt(i));
            if (target instanceof List<?> list) return list.get(i);
            if (target instanceof TupleValue tuple) return tuple.values.get(i);
            throw runtimeError("El objeto no admite índices");
        }

        private Object readSlice(Object target, Slice slice) {
            int size = length(target).intValueExact();
            int step = slice.step() == null ? 1 : toInt(eval(slice.step()), "slice step");
            if (step == 0) throw runtimeError("ValueError: slice step cannot be zero");

            int start = slice.start() == null
                    ? (step > 0 ? 0 : size - 1)
                    : normalizeSliceBound(toInt(eval(slice.start()), "slice start"), size);

            int stop = slice.stop() == null
                    ? (step > 0 ? size : -1)
                    : normalizeSliceBound(toInt(eval(slice.stop()), "slice stop"), size);

            List<Object> result = new ArrayList<>();
            if (target instanceof String s) {
                StringBuilder text = new StringBuilder();
                if (step > 0) {
                    for (int i = start; i < stop; i += step) text.append(s.charAt(i));
                } else {
                    for (int i = start; i > stop; i += step) text.append(s.charAt(i));
                }
                return text.toString();
            }

            List<Object> values = iterable(target);
            if (step > 0) {
                for (int i = start; i < stop; i += step) result.add(values.get(i));
            } else {
                for (int i = start; i > stop; i += step) result.add(values.get(i));
            }
            return result;
        }

        private int normalizeSliceBound(int value, int size) {
            if (value < 0) value += size;
            return Math.max(0, Math.min(size, value));
        }

        private int toInt(Object value, String what) {
            if (!(value instanceof BigInteger i)) throw runtimeError(what + " debe ser un entero");
            return i.intValueExact();
        }

        private void writeSubscript(Object target, Object index, Object value) {
            if (target instanceof Map<?, ?> raw) {
                @SuppressWarnings("unchecked") Map<Object, Object> map = (Map<Object, Object>) raw;
                map.put(index, value);
                return;
            }
            int i = indexValue(index, length(target));
            if (target instanceof List<?> raw) {
                @SuppressWarnings("unchecked") List<Object> list = (List<Object>) raw;
                list.set(i, value);
                return;
            }
            throw runtimeError("El objeto no admite asignaciones por índice");
        }

        private Object builtinPrint(List<Object> args, Map<String, Object> keywords) {
            String sep = keywords.getOrDefault("sep", " ") instanceof String s ? s : " ";
            String end = keywords.getOrDefault("end", "\n") instanceof String s ? s : "\n";
            for (int i = 0; i < args.size(); i++) {
                if (i > 0) OUTPUT.append(sep);
                OUTPUT.append(stringify(args.get(i)));
            }
            OUTPUT.append(end);
            return NONE;
        }

        Map<String, Object> variablesSnapshot() { return new LinkedHashMap<>(variables); }
    }

    @FunctionalInterface
    private interface Callable {
        Object call(List<Object> positional, Map<String, Object> keywords);
    }

    private static final class TupleValue {
        private final List<Object> values;
        private TupleValue(List<Object> values) { this.values = List.copyOf(values); }
    }

    private static Object unary(String operator, Object value) {
        return switch (operator) {
            case "+" -> numericUnary(value, false);
            case "-" -> numericUnary(value, true);
            case "not" -> !truthy(value);
            case "~" -> {
                if (!(value instanceof BigInteger i)) throw runtimeError("~ requiere un entero");
                yield i.not();
            }
            default -> throw runtimeError("Operador unario desconocido: " + operator);
        };
    }

    private static Object numericUnary(Object value, boolean negate) {
        if (value instanceof BigInteger i) return negate ? i.negate() : i;
        if (value instanceof Number n) return negate ? -n.doubleValue() : n.doubleValue();
        throw runtimeError("Se esperaba un número");
    }

    private static Object binary(Object left, String operator, Object right) {
        if (operator.equals("+") && left instanceof String a && right instanceof String b) return a + b;
        if (operator.equals("+") && left instanceof List<?> a && right instanceof List<?> b) {
            List<Object> result = new ArrayList<>(a);
            result.addAll(b);
            return result;
        }
        if (operator.equals("+") && left instanceof TupleValue a && right instanceof TupleValue b) {
            List<Object> result = new ArrayList<>(a.values);
            result.addAll(b.values);
            return new TupleValue(result);
        }
        if (operator.equals("*") && left instanceof String s && right instanceof BigInteger n) return s.repeat(n.intValueExact());
        if (operator.equals("*") && right instanceof String s && left instanceof BigInteger n) return s.repeat(n.intValueExact());
        if (operator.equals("*") && left instanceof List<?> list && right instanceof BigInteger n) {
            List<Object> result = new ArrayList<>();
            for (int i = 0; i < n.intValueExact(); i++) result.addAll(list);
            return result;
        }

        if (operator.equals("*") && right instanceof List<?> list && left instanceof BigInteger n) {
            List<Object> result = new ArrayList<>();
            for (int i = 0; i < n.intValueExact(); i++) result.addAll(list);
            return result;
        }

        if (operator.equals("@")) throw runtimeError("El operador @ aún no tiene implementación");

        if (left instanceof BigInteger a && right instanceof BigInteger b) {
            return switch (operator) {
                case "+" -> a.add(b);
                case "-" -> a.subtract(b);
                case "*" -> a.multiply(b);
                case "//" -> floorDivide(a, b);
                case "%" -> a.remainder(b);
                case "**" -> power(a, b);
                case "&" -> a.and(b);
                case "|" -> a.or(b);
                case "^" -> a.xor(b);
                case "<<" -> a.shiftLeft(b.intValueExact());
                case ">>" -> a.shiftRight(b.intValueExact());
                case "/" -> a.doubleValue() / b.doubleValue();
                default -> throw runtimeError("Operación no válida entre enteros: " + operator);
            };
        }

        if (left instanceof Number a && right instanceof Number b) {
            double x = a.doubleValue();
            double y = b.doubleValue();
            return switch (operator) {
                case "+" -> x + y;
                case "-" -> x - y;
                case "*" -> x * y;
                case "/" -> x / y;
                case "//" -> Math.floor(x / y);
                case "%" -> x % y;
                case "**" -> Math.pow(x, y);
                default -> throw runtimeError("Operación numérica no válida: " + operator);
            };
        }

        throw runtimeError("Operación no válida: " + typeName(left) + " " + operator + " " + typeName(right));
    }

    private static BigInteger floorDivide(BigInteger a, BigInteger b) {
        if (b.signum() == 0) throw runtimeError("ZeroDivisionError: integer division or modulo by zero");
        BigInteger[] qr = a.divideAndRemainder(b);
        if (a.signum() != b.signum() && qr[1].signum() != 0) return qr[0].subtract(BigInteger.ONE);
        return qr[0];
    }

    private static Object power(BigInteger base, BigInteger exponent) {
        if (exponent.signum() < 0) return Math.pow(base.doubleValue(), exponent.doubleValue());
        return base.pow(exponent.intValueExact());
    }

    private static boolean comparison(Object left, String operator, Object right) {
        return switch (operator) {
            case "==" -> Objects.equals(left, right);
            case "!=" -> !Objects.equals(left, right);
            case "is" -> left == right;
            case "is not" -> left != right;
            case "in" -> contains(right, left);
            case "not in" -> !contains(right, left);
            case "<", "<=", ">", ">=" -> compareValues(left, right, operator);
            default -> throw runtimeError("Comparación desconocida: " + operator);
        };
    }

    private static boolean contains(Object container, Object value) {
        if (container instanceof String s && value instanceof String v) return s.contains(v);
        if (container instanceof Map<?, ?> map) return map.containsKey(value);
        if (container instanceof Collection<?> collection) return collection.contains(value);
        return false;
    }

    private static boolean compareValues(Object left, Object right, String operator) {
        int result;
        if (left instanceof Number a && right instanceof Number b) result = Double.compare(a.doubleValue(), b.doubleValue());
        else if (left instanceof String a && right instanceof String b) result = a.compareTo(b);
        else throw runtimeError("Valores no comparables");
        return switch (operator) {
            case "<" -> result < 0;
            case "<=" -> result <= 0;
            case ">" -> result > 0;
            case ">=" -> result >= 0;
            default -> false;
        };
    }

    private static boolean truthy(Object value) {
        if (value == null) return false;
        if (value instanceof Boolean b) return b;
        if (value instanceof Number n) return n.doubleValue() != 0.0;
        if (value instanceof String s) return !s.isEmpty();
        if (value instanceof Collection<?> c) return !c.isEmpty();
        if (value instanceof Map<?, ?> m) return !m.isEmpty();
        return true;
    }

    private static BigInteger length(Object value) {
        if (value instanceof String s) return BigInteger.valueOf(s.length());
        if (value instanceof Collection<?> c) return BigInteger.valueOf(c.size());
        if (value instanceof Map<?, ?> m) return BigInteger.valueOf(m.size());
        if (value instanceof TupleValue t) return BigInteger.valueOf(t.values.size());
        throw runtimeError("object of type '" + typeName(value) + "' has no len()");
    }

    private static int indexValue(Object index, BigInteger size) {
        if (!(index instanceof BigInteger i)) throw runtimeError("Los índices deben ser enteros");
        int value = i.intValueExact();
        int length = size.intValueExact();
        if (value < 0) value += length;
        if (value < 0 || value >= length) throw runtimeError("IndexError: índice fuera de rango");
        return value;
    }

    private static List<Object> iterable(Object value) {
        if (value instanceof List<?> list) return new ArrayList<>(list);
        if (value instanceof TupleValue tuple) return new ArrayList<>(tuple.values);
        if (value instanceof Set<?> set) return new ArrayList<>(set);
        if (value instanceof String s) {
            List<Object> result = new ArrayList<>();
            for (int i = 0; i < s.length(); i++) result.add(String.valueOf(s.charAt(i)));
            return result;
        }
        if (value instanceof Map<?, ?> map) return new ArrayList<>(map.keySet());
        throw runtimeError("'" + typeName(value) + "' no es iterable");
    }

    private static List<Object> toList(Object value) { return value == null ? new ArrayList<>() : iterable(value); }

    private static Object convertInt(Object value) {
        if (value instanceof BigInteger) return value;
        if (value instanceof Number n) return BigInteger.valueOf((long) n.doubleValue());
        if (value instanceof Boolean b) return b ? BigInteger.ONE : BigInteger.ZERO;
        if (value instanceof String s) return new BigInteger(s.trim());
        throw runtimeError("int() no puede convertir " + typeName(value));
    }

    private static Object convertFloat(Object value) {
        if (value instanceof Number n) return n.doubleValue();
        if (value instanceof Boolean b) return b ? 1.0 : 0.0;
        if (value instanceof String s) return Double.parseDouble(s.trim());
        throw runtimeError("float() no puede convertir " + typeName(value));
    }

    private static Object minMax(List<Object> args, boolean minimum) {
        requireArgs(minimum ? "min" : "max", args, 1);
        List<Object> values = args.size() == 1 ? iterable(args.get(0)) : args;
        if (values.isEmpty()) throw runtimeError((minimum ? "min" : "max") + "() arg is an empty sequence");
        Object best = values.get(0);
        for (int i = 1; i < values.size(); i++) {
            Object candidate = values.get(i);
            if (compareValues(candidate, best, minimum ? "<" : ">")) best = candidate;
        }
        return best;
    }

    private static void requireArgs(String name, List<Object> args, int count) {
        if (args.size() < count) throw runtimeError(name + "() esperaba al menos " + count + " argumento(s)");
    }

    private static String typeName(Object value) {
        if (value == null) return "NoneType";
        if (value instanceof BigInteger) return "int";
        if (value instanceof Double || value instanceof Float) return "float";
        if (value instanceof Boolean) return "bool";
        if (value instanceof String) return "str";
        if (value instanceof List<?>) return "list";
        if (value instanceof TupleValue) return "tuple";
        if (value instanceof Set<?>) return "set";
        if (value instanceof Map<?, ?>) return "dict";
        if (value instanceof Callable) return "function";
        return value.getClass().getSimpleName();
    }

    private static String stringify(Object value) {
        if (value == null) return "None";
        if (value instanceof Boolean b) return b ? "True" : "False";
        if (value instanceof String s) return s;
        if (value instanceof List<?> list) return collectionString(list, "[", "]");
        if (value instanceof TupleValue tuple) return collectionString(tuple.values, "(", ")");
        if (value instanceof Set<?> set) return collectionString(set, "{", "}");
        if (value instanceof Map<?, ?> map) {
            List<String> parts = new ArrayList<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) parts.add(stringify(entry.getKey()) + ": " + stringify(entry.getValue()));
            return "{" + String.join(", ", parts) + "}";
        }
        return String.valueOf(value);
    }

    private static String collectionString(Collection<?> values, String open, String close) {
        List<String> parts = new ArrayList<>();
        for (Object value : values) parts.add(stringify(value));
        String separator = ", ";
        String body = String.join(separator, parts);
        if (open.equals("(") && values.size() == 1) body += ",";
        return open + body + close;
    }

    private static RuntimeException runtimeError(String message) {
        return new IllegalArgumentException(message);
    }
}
