package textmenu.runtime;

public class ScriptError extends RuntimeException {

    private final String type;

    private ScriptError(String type, String message) {
        super(message);
        this.type = type;
    }

    public static ScriptError runtime(String message) {
        return new ScriptError("RuntimeError", message);
    }

    public static ScriptError value(String message) {
        return new ScriptError("ValueError", message);
    }

    public static ScriptError type(String message) {
        return new ScriptError("TypeError", message);
    }

    public String type() {
        return type;
    }
}
