package textmenu.editor;

import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;

/**
 * Keyboard shortcuts for CodeEditorWidget.
 */
public final class MCodeEditorKeyHandler {

    private MCodeEditorKeyHandler() {}

    public static boolean handle(
            MCodeEditorModel model,
            int keyCode,
            int modifiers,
            MinecraftClient client,
            int visibleRows
    ) {
        boolean shift = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;
        boolean ctrl = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;

        switch (keyCode) {
            case GLFW.GLFW_KEY_A -> {
                if (!ctrl) return false;
                model.selectAll();
                return true;
            }

            case GLFW.GLFW_KEY_C -> {
                if (!ctrl) return false;
                String value = model.copy();
                if (!value.isEmpty()) client.keyboard.setClipboard(value);
                return true;
            }

            case GLFW.GLFW_KEY_X -> {
                if (!ctrl) return false;
                String value = model.cut();
                if (!value.isEmpty()) client.keyboard.setClipboard(value);
                return true;
            }

            case GLFW.GLFW_KEY_V -> {
                if (!ctrl) return false;
                model.paste(client.keyboard.getClipboard());
                return true;
            }

            case GLFW.GLFW_KEY_Z -> {
                if (!ctrl) return false;
                if (shift) model.redo();
                else model.undo();
                return true;
            }

            case GLFW.GLFW_KEY_Y -> {
                if (!ctrl) return false;
                model.redo();
                return true;
            }

            case GLFW.GLFW_KEY_LEFT -> {
                model.moveLeft(shift, ctrl);
                return true;
            }

            case GLFW.GLFW_KEY_RIGHT -> {
                model.moveRight(shift, ctrl);
                return true;
            }

            case GLFW.GLFW_KEY_UP -> {
                model.moveUp(shift);
                return true;
            }

            case GLFW.GLFW_KEY_DOWN -> {
                model.moveDown(shift);
                return true;
            }

            case GLFW.GLFW_KEY_HOME -> {
                model.moveHome(shift, ctrl);
                return true;
            }

            case GLFW.GLFW_KEY_END -> {
                model.moveEnd(shift, ctrl);
                return true;
            }

            case GLFW.GLFW_KEY_PAGE_UP -> {
                model.pageUp(visibleRows, shift);
                return true;
            }

            case GLFW.GLFW_KEY_PAGE_DOWN -> {
                model.pageDown(visibleRows, shift);
                return true;
            }

            case GLFW.GLFW_KEY_BACKSPACE -> {
                if (ctrl) {
                    deletePreviousWord(model);
                } else {
                    model.backspace();
                }
                return true;
            }

            case GLFW.GLFW_KEY_DELETE -> {
                model.delete();
                return true;
            }

            case GLFW.GLFW_KEY_TAB -> {
                if (shift) model.unindent();
                else model.insertTab();
                return true;
            }
        }

        return false;
    }

    private static void deletePreviousWord(MCodeEditorModel model) {
        if (model.hasSelection()) {
            model.backspace();
            return;
        }

        int before = model.getCursor();
        if (before <= 0) return;

        model.moveLeft(false, true);
        int after = model.getCursor();

        while (model.getCursor() < before) model.delete();

        if (model.getCursor() != after) {
            model.setCursor(after);
        }
    }
}
