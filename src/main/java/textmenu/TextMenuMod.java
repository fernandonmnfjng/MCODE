package textmenu;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import textmenu.screen.TextMenuScreen;

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

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openMenuKey.wasPressed()) {
                if (client.currentScreen == null) {
                    client.setScreen(new TextMenuScreen());
                }
            }
        });
    }
}
