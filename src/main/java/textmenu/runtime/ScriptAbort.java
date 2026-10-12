package textmenu.runtime;

public final class ScriptAbort extends Error {

    public enum Reason { CANCELLED, LIMIT }

    private final Reason reason;

    public ScriptAbort(Reason reason, String message) {
        super(message, null, false, false);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
