package textmenu;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import textmenu.draw.Scene;
import textmenu.draw.SceneRenderer;
import textmenu.interpreter.MCodeProjectManager;
import textmenu.runtime.MainThreadQueue;
import textmenu.runtime.PlayerSnapshot;
import textmenu.runtime.ScriptMode;
import textmenu.runtime.ScriptRunner;
import textmenu.screen.TextMenuScreen;

import java.nio.file.Path;

public class TextMenuMod implements ClientModInitializer {

    public static final String MOD_ID = "mcode";
    private static KeyBinding openMenuKey;

    @Override
    public void onInitializeClient() {
        openMenuKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.textmenu.open",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_M,
                "category.textmenu"
        ));

        try {
            Path gameDir = FabricLoader.getInstance().getGameDir().resolve("mcode");
            MCodeProjectManager.setWorkspaceRoot(gameDir);
            MCodeProjectManager.initialize();
        } catch (Exception ignored) {
        }

        SceneRenderer.register();

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            PlayerSnapshot.update(client);
            ScriptMode.update(client);
            MainThreadQueue.drain();
            while (openMenuKey.wasPressed()) {
                if (client.currentScreen == null) {
                    client.setScreen(new TextMenuScreen());
                }
            }
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            ScriptRunner.stop();
            MainThreadQueue.clear();
            Scene.global().clear();
        });

        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> ScriptRunner.shutdown());
    }
}
