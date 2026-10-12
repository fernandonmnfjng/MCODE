package textmenu.runtime;

import net.minecraft.client.MinecraftClient;

/**
 * Permission level of running scripts.
 *
 * LOCAL  - only client-side things: draw shapes, show local chat lines, read
 *          what the client already knows (own position). Nothing is sent to a server.
 * GLOBAL - also allowed to act on the world through the server (mc.say: chat and commands).
 *
 * On a multiplayer server the effective mode is always LOCAL, whatever is selected.
 * GLOBAL is only honoured in single player (integrated server).
 */
public final class ScriptMode {

    public enum Mode {
        LOCAL,
        GLOBAL
    }

    private static volatile Mode selected = Mode.LOCAL;
    private static volatile boolean remoteServer;

    private ScriptMode() {
    }

    public static void update(MinecraftClient client) {
        remoteServer = client.world != null && client.getServer() == null;
    }

    public static boolean onRemoteServer() {
        return remoteServer;
    }

    public static Mode selected() {
        return selected;
    }

    public static Mode effective() {
        return remoteServer ? Mode.LOCAL : selected;
    }

    public static void select(Mode mode) {
        selected = mode;
    }

    public static Mode toggle() {
        selected = selected == Mode.LOCAL ? Mode.GLOBAL : Mode.LOCAL;
        return selected;
    }

    public static void requireGlobal(String function) {
        if (effective() == Mode.GLOBAL) return;
        String reason = remoteServer
                ? "en servidores multijugador MCODE solo funciona en modo LOCAL."
                : "usa mc.mode(\"global\") o cambia a modo GLOBAL con el botón del editor.";
        throw ScriptError.runtime(function + "() requiere modo GLOBAL: " + reason);
    }
}
