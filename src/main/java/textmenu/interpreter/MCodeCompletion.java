package textmenu.interpreter;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Context-aware completion for the MCODE editor.
 * It is deliberately independent from the UI so the same completion engine
 * can later serve Tab, Ctrl+Space and mouse-selected completion popups.
 */
public final class MCodeCompletion {
    private MCodeCompletion() {}

    public record Suggestion(
            String label,
            String insertText,
            String detail
    ) {}

    private static final Pattern MEMBER_PATTERN =
            Pattern.compile("([A-Za-z_][A-Za-z0-9_]*(?:\\.[A-Za-z_][A-Za-z0-9_]*)*)\\.([A-Za-z_][A-Za-z0-9_]*)?$");

    private static final Set<String> TYPES = Set.of(
            "int", "float", "str", "bool", "list", "tuple", "set", "dict",
            "range", "object", "Exception", "ValueError", "TypeError", "NameError",
            "IndexError", "KeyError", "RuntimeError", "ImportError", "ModuleNotFoundError"
    );

    private static final Set<String> MODULES = Set.of("time", "math");

    public static List<Suggestion> complete(String source, int cursor) {
        if (source == null) source = "";
        cursor = Math.max(0, Math.min(cursor, source.length()));

        String before = source.substring(0, cursor);
        Matcher memberMatcher = MEMBER_PATTERN.matcher(before);

        if (memberMatcher.find()) {
            String expression = memberMatcher.group(1);
            String prefix = memberMatcher.group(2) == null ? "" : memberMatcher.group(2);
            Object value = MCodeInterpreter.resolveCompletionExpression(expression);
            if (value != null) {
                List<Suggestion> suggestions = new ArrayList<>();
                for (String name : MCodeInterpreter.getCompletionMembers(value)) {
                    if (name.startsWith(prefix)) {
                        Object memberValue=MCodeInterpreter.resolveCompletionExpression(expression+"."+name);
                        String insert=MCodeInterpreter.isCompletionCallable(memberValue)?name+"()":name;
                        suggestions.add(new Suggestion(name, insert, memberDetail(value, name)));
                    }
                }
                return sort(suggestions);
            }
        }

        String prefix = identifierPrefix(before);
        LinkedHashMap<String, Suggestion> suggestions = new LinkedHashMap<>();

        for (String name : MCodeInterpreter.getBuiltinNames()) {
            if (name.startsWith(prefix)) {
                String insert = isCallableBuiltin(name) ? name + "()" : name;
                suggestions.putIfAbsent(name, new Suggestion(name, insert, "built-in"));
            }
        }

        for (String name : MCodeInterpreter.getKeywordNames()) {
            if (name.startsWith(prefix)) {
                suggestions.putIfAbsent(name, new Suggestion(name, name, "keyword"));
            }
        }

        for (String name : TYPES) {
            if (name.startsWith(prefix)) {
                suggestions.putIfAbsent(name, new Suggestion(name, name, "type"));
            }
        }

        for (String name : MODULES) {
            if (name.startsWith(prefix)) {
                suggestions.putIfAbsent(name, new Suggestion(name, name, "module"));
            }
        }

        for (String name : MCodeInterpreter.getVariables().keySet()) {
            if (name.startsWith(prefix)) {
                if (!suggestions.containsKey(name)) suggestions.put(name, new Suggestion(name, name, "variable"));
            }
        }

        // Also expose identifiers already present in the current source.
        try {
            for (MCodeToken token : new MCodeLexer().tokenize(before)) {
                if (token.type() == MCodeToken.Type.IDENTIFIER && token.text().startsWith(prefix)) {
                    if (!token.text().equals(prefix)) suggestions.putIfAbsent(token.text(), new Suggestion(token.text(), token.text(), "identifier"));
                }
            }
        } catch (RuntimeException ignored) {
            // Completion must never break typing because the document is incomplete.
        }

        return sort(new ArrayList<>(suggestions.values()));
    }

    private static String identifierPrefix(String text) {
        int i = text.length() - 1;
        while (i >= 0) {
            char c = text.charAt(i);
            if (Character.isLetterOrDigit(c) || c == '_') i--;
            else break;
        }
        return text.substring(i + 1);
    }

    private static boolean isCallableBuiltin(String name) {
        return Set.of(
                "print", "len", "type", "int", "float", "str", "bool", "list", "tuple", "set",
                "dict", "range", "abs", "round", "min", "max", "sum", "any", "all", "enumerate",
                "zip", "sorted", "reversed", "isinstance", "issubclass", "repr", "ascii", "format",
                "hex", "oct", "bin", "ord", "chr", "hash", "id", "callable", "dir", "getattr",
                "setattr", "hasattr", "delattr", "vars", "super", "sleep", "pow", "divmod", "iter",
                "next", "map", "filter"
        ).contains(name);
    }

    private static String memberDetail(Object value, String name) {
        return "member";
    }

    private static List<Suggestion> sort(List<Suggestion> values) {
        values.sort(Comparator.comparing(Suggestion::label, String.CASE_INSENSITIVE_ORDER));
        return values;
    }
}
