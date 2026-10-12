package textmenu.screen;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ConfirmScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import textmenu.draw.Scene;
import textmenu.editor.MCodeConsoleWidget;
import textmenu.interpreter.MCodeFileDialogs;
import textmenu.interpreter.MCodeProjectManager;
import textmenu.interpreter.MCodeSource;
import textmenu.runtime.ConsoleBuffer;
import textmenu.runtime.ScriptMode;
import textmenu.runtime.ScriptRunner;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

public class TextMenuScreen extends Screen {
    private static final Text RUN_LABEL = Text.literal("Ejecutar");
    private static final Text STOP_LABEL = Text.literal("Detener");
    private static final List<Tab> TABS = new ArrayList<>();
    private static int activeTab;
    private static boolean sessionLoaded;

    private CodeEditorWidget codeEditor;
    private MCodeConsoleWidget consoleWidget;
    private ButtonWidget runButton;
    private ButtonWidget modeButton;
    private long shownRevision = -1;
    private final List<ButtonWidget> tabButtons = new ArrayList<>();
    private final List<ButtonWidget> tabCloseButtons = new ArrayList<>();
    private final List<Integer> tabButtonIndices = new ArrayList<>();
    private int firstVisibleTabIndex;
    private ButtonWidget previousTabsButton;
    private ButtonWidget nextTabsButton;
    private int tabScrollbarX, tabScrollbarY, tabScrollbarWidth, tabScrollbarThumbWidth, tabScrollbarThumbX;
    private boolean draggingTabScrollbar;
    private double tabScrollbarDragOffset;

    private static final class Tab {
        String title;
        String code = "";
        String savedCode = "";
        String path;
        boolean dirty;

        Tab(int n) { title = "Script " + n; }

        String runKey() {
            try {
                String declared = MCodeSource.parse(code).path();
                if (declared != null && !declared.isBlank()) return declared;
            } catch (RuntimeException ignored) { }
            return path != null && !path.isBlank() ? path : "tab-" + title;
        }

        String displayName() {
            String effectivePath = path;
            try {
                String declared = MCodeSource.parse(code).path();
                if (declared != null && !declared.isBlank()) effectivePath = declared;
            } catch (RuntimeException ignored) { }
            if (effectivePath != null && !effectivePath.isBlank()) {
                String p = effectivePath.replace('\\', '/');
                int slash = p.lastIndexOf('/');
                String leaf = p.substring(slash + 1);
                return leaf.replaceFirst("(?i)\\.mcode$", "");
            }
            return title == null || title.isBlank() ? "Script" : title.replaceFirst("(?i)\\.mcode$", "");
        }
    }

    public TextMenuScreen() { super(Text.literal("MCODE IDE 2.4.1")); }

    private static void ensureTabs() {
        if (!sessionLoaded) loadSession();
        if (TABS.isEmpty()) TABS.add(new Tab(1));
        activeTab = Math.max(0, Math.min(activeTab, TABS.size() - 1));
    }

    private static void loadSession() {
        sessionLoaded = true;
        Path file = MCodeProjectManager.getWorkspaceRoot().resolve("editor-session.dat");
        if (!Files.isRegularFile(file)) return;
        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                String[] p = line.split("\\t", 5);
                if (p.length < 4) continue;
                Tab t = new Tab(TABS.size() + 1);
                t.title = decode(p[0]);
                t.path = p[1].equals("-") ? null : decode(p[1]);
                t.dirty = p[2].equals("1");
                t.code = decode(p[3]);
                // Backward compatibility with session files written before savedCode was stored.
                t.savedCode = p.length >= 5 ? decode(p[4]) : (t.dirty ? "\u0000UNSAVED\u0000" : t.code);
                TABS.add(t);
                if (TABS.size() >= 20) break;
            }
        } catch (Exception ignored) {
            TABS.clear();
        }
        if (TABS.isEmpty()) TABS.add(new Tab(1));
    }

    private static String encode(String s) {
        return Base64.getEncoder().encodeToString((s == null ? "" : s).getBytes(StandardCharsets.UTF_8));
    }

    private static String decode(String s) {
        return new String(Base64.getDecoder().decode(s), StandardCharsets.UTF_8);
    }

    private static void persistSession() {
        try {
            Path root = MCodeProjectManager.getWorkspaceRoot();
            Files.createDirectories(root);
            StringBuilder out = new StringBuilder();
            for (Tab t : TABS) {
                out.append(encode(t.title)).append('\t')
                        .append(t.path == null ? "-" : encode(t.path)).append('\t')
                        .append(t.dirty ? '1' : '0').append('\t')
                        .append(encode(t.code)).append('\t')
                        .append(encode(t.savedCode)).append('\n');
            }
            Files.writeString(root.resolve("editor-session.dat"), out.toString(), StandardCharsets.UTF_8);
        } catch (Exception ignored) { }
    }

    private Tab tab() {
        ensureTabs();
        return TABS.get(activeTab);
    }

    private void captureEditor() {
        if (codeEditor == null || TABS.isEmpty()) return;
        Tab current = tab();
        String currentCode = codeEditor.getCode();
        if (!currentCode.equals(current.code)) {
            current.code = currentCode;
            current.dirty = !currentCode.equals(current.savedCode);
            persistSession();
            refreshTabLabels();
        }
    }

    @Override
    protected void init() {
        ensureTabs();
        int centerX = width / 2;
        int totalWidth = Math.min(700, width - 24);
        int startX = centerX - totalWidth / 2;
        int tabsY = 23;
        int tabsHeight = 18;
        tabButtons.clear();
        tabCloseButtons.clear();
        tabButtonIndices.clear();

        int visibleTabs = Math.min(TABS.size(), 5);
        firstVisibleTabIndex = Math.max(0, Math.min(firstVisibleTabIndex, Math.max(0, TABS.size() - visibleTabs)));
        int arrowWidth = 19;
        int plusWidth = 24;
        int leftArrowX = startX;
        int tabStartX = startX + arrowWidth + 3;
        int plusX = startX + totalWidth - plusWidth;
        int rightArrowX = plusX - arrowWidth - 3;
        int tabsAreaWidth = Math.max(80, rightArrowX - tabStartX - 3);
        int groupWidth = Math.max(42, (tabsAreaWidth - (visibleTabs - 1) * 3) / Math.max(1, visibleTabs));
        int closeWidth = 16;
        int tabWidth = Math.max(28, groupWidth - closeWidth);
        tabScrollbarX = tabStartX;
        tabScrollbarY = tabsY + tabsHeight + 1;
        tabScrollbarWidth = tabsAreaWidth;
        previousTabsButton = ButtonWidget.builder(Text.literal("‹"), button -> scrollTabs(-1))
                .dimensions(leftArrowX, tabsY, arrowWidth, tabsHeight).build();
        previousTabsButton.active = firstVisibleTabIndex > 0;
        addDrawableChild(previousTabsButton);
        int x = tabStartX;
        for (int i = 0; i < visibleTabs; i++) {
            final int slot = i;
            final int idx = firstVisibleTabIndex + i;
            if (idx >= TABS.size()) break;
            Tab t = TABS.get(idx);
            ButtonWidget titleButton = ButtonWidget.builder(tabLabel(idx, t), button -> switchTab(firstVisibleTabIndex + slot))
                    .dimensions(x, tabsY, tabWidth, tabsHeight).build();
            tabButtons.add(titleButton);
            tabButtonIndices.add(idx);
            addDrawableChild(titleButton);
            ButtonWidget closeTabButton = ButtonWidget.builder(Text.literal("×"), button -> requestCloseTab(firstVisibleTabIndex + slot))
                    .dimensions(x + tabWidth, tabsY, closeWidth, tabsHeight).build();
            closeTabButton.active = TABS.size() > 1;
            tabCloseButtons.add(closeTabButton);
            addDrawableChild(closeTabButton);
            x += groupWidth + 3;
        }
        updateTabScrollbarGeometry();
        nextTabsButton = ButtonWidget.builder(Text.literal("›"), button -> scrollTabs(1))
                .dimensions(rightArrowX, tabsY, arrowWidth, tabsHeight).build();
        nextTabsButton.active = firstVisibleTabIndex + visibleTabs < TABS.size();
        addDrawableChild(nextTabsButton);
        addDrawableChild(ButtonWidget.builder(Text.literal("+"), b -> newTab())
                .dimensions(plusX, tabsY, plusWidth, tabsHeight).build());

        int topY = 45;
        int buttonHeight = 20;
        int spacing = 5;
        int availableHeight = height - topY - 10;
        int contentHeight = availableHeight - buttonHeight - spacing * 2;
        int editorHeight = Math.max(65, (int) (contentHeight * 0.62));
        int consoleHeight = Math.max(42, contentHeight - editorHeight);
        int editorY = topY;
        int buttonY = editorY + editorHeight + spacing;
        int consoleY = buttonY + buttonHeight + spacing;

        codeEditor = new CodeEditorWidget(startX, editorY, totalWidth, editorHeight, Text.literal("Editor"), textRenderer);
        codeEditor.setCode(tab().code);
        addDrawableChild(codeEditor);

        int buttonCount = 9, buttonSpacing = 4;
        int buttonWidth = Math.min(84, (totalWidth - buttonSpacing * (buttonCount - 1)) / buttonCount);
        int buttonsTotalWidth = buttonWidth * buttonCount + buttonSpacing * (buttonCount - 1);
        int buttonStartX = centerX - buttonsTotalWidth / 2;
        int step = buttonWidth + buttonSpacing;
        runButton = ButtonWidget.builder(RUN_LABEL, b -> toggleRun()).dimensions(buttonStartX, buttonY, buttonWidth, buttonHeight).build();
        addDrawableChild(runButton);
        addDrawableChild(ButtonWidget.builder(Text.literal("Abrir"), b -> openFile()).dimensions(buttonStartX + step, buttonY, buttonWidth, buttonHeight).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Guardar"), b -> saveFile()).dimensions(buttonStartX + step * 2, buttonY, buttonWidth, buttonHeight).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Limpiar"), b -> {
            captureEditor();
            tab().code = "";
            tab().dirty = !tab().code.equals(tab().savedCode);
            codeEditor.setCode("");
            ScriptRunner.console().clear();
            refreshTabLabels();
            persistSession();
        }).dimensions(buttonStartX + step * 3, buttonY, buttonWidth, buttonHeight).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Escena"), b -> Scene.global().clear())
                .dimensions(buttonStartX + step * 4, buttonY, buttonWidth, buttonHeight).build());
        modeButton = ButtonWidget.builder(Text.literal("Local"), b -> {
            ScriptMode.Mode mode = ScriptMode.toggle();
            ScriptRunner.console().append(mode == ScriptMode.Mode.GLOBAL
                    ? "Modo GLOBAL: coordenadas absolutas.\n" : "Modo LOCAL: anclado al jugador.\n");
        }).dimensions(buttonStartX + step * 5, buttonY, buttonWidth, buttonHeight).build();
        addDrawableChild(modeButton);
        addDrawableChild(ButtonWidget.builder(Text.literal("Recursos"), b -> client.setScreen(new MCodeResourcesScreen(this))).dimensions(buttonStartX + step * 6, buttonY, buttonWidth, buttonHeight).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Cerrar"), b -> close()).dimensions(buttonStartX + step * 7, buttonY, buttonWidth, buttonHeight).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Copiar log"), b -> copyConsoleOutput()).dimensions(buttonStartX + step * 8, buttonY, buttonWidth, buttonHeight).build());
        consoleWidget = new MCodeConsoleWidget(startX, consoleY, totalWidth, consoleHeight, Text.literal("Consola"), textRenderer);
        addDrawableChild(consoleWidget);
        shownRevision = -1;
        syncState();
    }

    private Text tabLabel(int idx, Tab tab) {
        return Text.literal((idx == activeTab ? "[" : "") + tab.displayName() + (tab.dirty ? " *" : "") + (idx == activeTab ? "]" : ""));
    }

    private void scrollTabs(int delta) {
        int visible = Math.min(TABS.size(), 5);
        int maxFirst = Math.max(0, TABS.size() - visible);
        int next = Math.max(0, Math.min(maxFirst, firstVisibleTabIndex + delta));
        if (next == firstVisibleTabIndex) return;
        firstVisibleTabIndex = next;
        refreshTabStripPositions();
    }

    private void updateTabScrollbarGeometry() {
        int visible = Math.min(TABS.size(), 5);
        if (TABS.size() <= visible || tabScrollbarWidth <= 0) {
            tabScrollbarThumbWidth = tabScrollbarWidth;
            tabScrollbarThumbX = tabScrollbarX;
            return;
        }
        tabScrollbarThumbWidth = Math.max(20, (int) Math.round(tabScrollbarWidth * (visible / (double) TABS.size())));
        tabScrollbarThumbWidth = Math.min(tabScrollbarWidth, tabScrollbarThumbWidth);
        int maxFirst = TABS.size() - visible;
        int travel = Math.max(0, tabScrollbarWidth - tabScrollbarThumbWidth);
        tabScrollbarThumbX = tabScrollbarX + (maxFirst == 0 ? 0 : (int) Math.round(travel * (firstVisibleTabIndex / (double) maxFirst)));
    }

    /** Repositions the existing tab widgets so scrolling does not reset editor focus/cursor. */
    private void refreshTabStripPositions() {
        int totalWidth = Math.min(700, width - 24);
        int startX = width / 2 - totalWidth / 2;
        int tabsY = 23;
        int arrowWidth = 19;
        int plusWidth = 24;
        int tabStartX = startX + arrowWidth + 3;
        int plusX = startX + totalWidth - plusWidth;
        int rightArrowX = plusX - arrowWidth - 3;
        int tabsAreaWidth = Math.max(80, rightArrowX - tabStartX - 3);
        int visibleTabs = Math.min(TABS.size(), 5);
        int groupWidth = Math.max(42, (tabsAreaWidth - (visibleTabs - 1) * 3) / Math.max(1, visibleTabs));
        int closeWidth = 16;
        int tabWidth = Math.max(28, groupWidth - closeWidth);
        int x = tabStartX;
        for (int i = 0; i < tabButtons.size(); i++) {
            int index = firstVisibleTabIndex + i;
            if (index >= TABS.size()) break;
            tabButtonIndices.set(i, index);
            ButtonWidget title = tabButtons.get(i);
            title.setX(x);
            title.setMessage(tabLabel(index, TABS.get(index)));
            ButtonWidget close = tabCloseButtons.get(i);
            close.setX(x + tabWidth);
            close.active = TABS.size() > 1;
            x += groupWidth + 3;
        }
        if (previousTabsButton != null) previousTabsButton.active = firstVisibleTabIndex > 0;
        if (nextTabsButton != null) nextTabsButton.active = firstVisibleTabIndex + visibleTabs < TABS.size();
        updateTabScrollbarGeometry();
    }

    private void scrollTabsToPointer(double mouseX) {
        int visible = Math.min(TABS.size(), 5);
        int maxFirst = Math.max(0, TABS.size() - visible);
        int travel = Math.max(1, tabScrollbarWidth - tabScrollbarThumbWidth);
        double thumbLeft = Math.max(tabScrollbarX, Math.min(tabScrollbarX + travel, mouseX - tabScrollbarDragOffset));
        int next = (int) Math.round((thumbLeft - tabScrollbarX) / travel * maxFirst);
        next = Math.max(0, Math.min(maxFirst, next));
        if (next != firstVisibleTabIndex) {
            firstVisibleTabIndex = next;
            refreshTabStripPositions();
        }
    }

    private void navigateTab(int delta) {
        captureEditor();
        if (TABS.isEmpty()) return;
        activeTab = Math.floorMod(activeTab + delta, TABS.size());
        int visible = Math.min(TABS.size(), 5);
        if (activeTab < firstVisibleTabIndex) firstVisibleTabIndex = activeTab;
        if (activeTab >= firstVisibleTabIndex + visible) firstVisibleTabIndex = activeTab - visible + 1;
        persistSession();
        rebuildScreenUi();
    }

    private void switchTab(int index) {
        captureEditor();
        if (index < 0 || index >= TABS.size()) return;
        activeTab = index;
        int visible = Math.min(TABS.size(), 5);
        if (activeTab < firstVisibleTabIndex) firstVisibleTabIndex = activeTab;
        else if (activeTab >= firstVisibleTabIndex + visible) firstVisibleTabIndex = activeTab - visible + 1;
        persistSession();
        rebuildScreenUi();
    }

    private int nextTabNumber() {
        int n = 1;
        while (true) {
            String candidate = "Script " + n;
            boolean used = false;
            for (Tab t : TABS) if (t.title.equals(candidate)) { used = true; break; }
            if (!used) return n;
            n++;
        }
    }

    private void newTab() {
        captureEditor();
        if (TABS.size() >= 20) {
            ScriptRunner.console().append("Límite de 20 pestañas alcanzado.\n");
            return;
        }
        TABS.add(new Tab(nextTabNumber()));
        activeTab = TABS.size() - 1;
        firstVisibleTabIndex = Math.max(0, TABS.size() - 5);
        persistSession();
        rebuildScreenUi();
    }

    private void requestCloseTab(int index) {
        captureEditor();
        if (TABS.size() <= 1 || index < 0 || index >= TABS.size()) return;
        Tab target = TABS.get(index);
        if (!target.dirty) {
            removeTab(target);
            return;
        }
        client.setScreen(new ConfirmScreen(confirmed -> {
            client.setScreen(this);
            if (confirmed) saveTabThenClose(target);
            else removeTab(target);
        }, Text.literal("Guardar antes de cerrar"),
                Text.literal("Sí: guardar los cambios y cerrar. No: descartar los cambios y cerrar.")));
    }

    private void removeTab(Tab target) {
        if (TABS.size() <= 1) return;
        int index = TABS.indexOf(target);
        if (index < 0) return;
        ScriptRunner.stopParallel(target.runKey());
        TABS.remove(index);
        if (index < activeTab) activeTab--;
        else if (index == activeTab) activeTab = Math.min(activeTab, TABS.size() - 1);
        activeTab = Math.max(0, activeTab);
        firstVisibleTabIndex = Math.max(0, Math.min(firstVisibleTabIndex, Math.max(0, TABS.size() - Math.min(TABS.size(), 5))));
        persistSession();
        rebuildScreenUi();
    }

    private void saveTabThenClose(Tab target) {
        int idx = TABS.indexOf(target);
        if (idx < 0) return;
        if (idx == activeTab) captureEditor();
        MCodeSource.Parsed parsed;
        try {
            parsed = MCodeSource.parse(target.code);
        } catch (RuntimeException ex) {
            showMessage("Error en SetPATH: " + ex.getMessage());
            return;
        }
        String path = parsed.path() != null ? parsed.path() : target.path;
        if (path != null && !path.isBlank()) {
            try {
                target.path = path;
                Path file = MCodeProjectManager.normalizeSourcePath(path);
                Runnable afterSave = () -> removeTab(target);
                if (Files.exists(file)) {
                    client.setScreen(new ConfirmScreen(confirmed -> {
                        client.setScreen(this);
                        if (confirmed) writeToPath(target, file, afterSave);
                    }, Text.literal("Sobrescribir archivo"), Text.literal("Ya existe " + path + ".mcode. ¿Deseas sobrescribirlo?")));
                } else {
                    writeToPath(target, file, afterSave);
                }
            } catch (Exception ex) {
                showMessage("Error al guardar: " + ex.getMessage());
            }
            return;
        }
        Path chosen = MCodeFileDialogs.chooseSave(MCodeProjectManager.getWorkspaceRoot(), target.displayName() + ".mcode");
        if (chosen == null) {
            String error = MCodeFileDialogs.consumeLastError();
            if (error != null) showMessage("Error al abrir selector: " + error);
            return;
        }
        Path normalized = chosen.toAbsolutePath().normalize();
        Runnable afterSave = () -> removeTab(target);
        if (Files.exists(normalized)) {
            client.setScreen(new ConfirmScreen(confirmed -> {
                client.setScreen(this);
                if (confirmed) writeToChosen(target, normalized, afterSave);
            }, Text.literal("Sobrescribir archivo"), Text.literal("El archivo ya existe. ¿Deseas sobrescribirlo?")));
        } else {
            writeToChosen(target, normalized, afterSave);
        }
    }

    private void rebuildScreenUi() { clearChildren(); init(); }

    private void refreshTabLabels() {
        for (int i = 0; i < tabButtons.size(); i++) {
            int index = tabButtonIndices.get(i);
            if (index >= 0 && index < TABS.size()) tabButtons.get(i).setMessage(tabLabel(index, TABS.get(index)));
        }
    }

    private void toggleRun() {
        captureEditor();
        Tab current = tab();
        if (ScriptRunner.isParallelRunning(current.runKey())) {
            ScriptRunner.stopParallel(current.runKey());
            ScriptRunner.console().append("Deteniendo " + current.displayName() + "...\n");
            return;
        }
        try {
            MCodeSource.Parsed parsed = MCodeSource.parse(current.code);
            String key = parsed.path() == null ? current.runKey() : parsed.path();
            if (parsed.path() != null) {
                for (int i = 0; i < TABS.size(); i++) if (i != activeTab) {
                    Tab other = TABS.get(i);
                    String otherPath = other.path;
                    try {
                        String declared = MCodeSource.parse(other.code).path();
                        if (declared != null) otherPath = declared;
                    } catch (Exception ignored) { }
                    if (parsed.path().equals(otherPath)) {
                        ScriptRunner.console().append("Error: SetPATH ya está en uso por otra pestaña: " + parsed.path() + "\n");
                        return;
                    }
                }
                current.path = parsed.path();
            }
            ScriptRunner.startParallel(key, current.code);
        } catch (RuntimeException ex) {
            ScriptRunner.console().append("Error: " + ex.getMessage() + "\n");
        }
        persistSession();
    }

    private void syncState() {
        captureEditor();
        ConsoleBuffer console = ScriptRunner.console();
        long revision = console.revision();
        if (consoleWidget != null && revision != shownRevision) {
            shownRevision = revision;
            consoleWidget.replaceLines(console.snapshot());
        }
        if (runButton != null) runButton.setMessage(ScriptRunner.isParallelRunning(tab().runKey()) ? STOP_LABEL : RUN_LABEL);
        if (modeButton != null) {
            modeButton.setMessage(Text.literal(ScriptMode.effective() == ScriptMode.Mode.GLOBAL ? "Global" : "Local"));
            modeButton.active = !ScriptMode.onRemoteServer();
        }
    }

    /** Copies the full buffered console log (including the current partial line) to the OS clipboard. */
    private void copyConsoleOutput() {
        List<String> lines = ScriptRunner.console().snapshot();
        String output = String.join("\n", lines);
        if (output.isBlank()) {
            ScriptRunner.console().append("[MCODE] La consola está vacía; no hay salida para copiar.\n");
            return;
        }
        try {
            net.minecraft.client.MinecraftClient.getInstance().keyboard.setClipboard(output);
            ScriptRunner.console().append("[MCODE] Salida de la consola copiada al portapapeles.\n");
        } catch (RuntimeException ex) {
            ScriptRunner.console().append("[MCODE] No se pudo copiar la consola: " + ex.getMessage() + "\n");
        }
    }

    private void showMessage(String message) { ScriptRunner.console().set(message); }

    private void openFile() {
        Path chosen = MCodeFileDialogs.chooseOpen(MCodeProjectManager.getWorkspaceRoot());
        if (chosen == null) {
            String error = MCodeFileDialogs.consumeLastError();
            if (error != null) showMessage("Error al abrir selector: " + error);
            return;
        }
        try {
            String content = Files.readString(chosen, StandardCharsets.UTF_8);
            MCodeSource.Parsed parsed = MCodeSource.parse(content);
            Tab current = tab();
            current.code = content;
            current.savedCode = content;
            current.path = parsed.path();
            Path normalized = chosen.toAbsolutePath().normalize();
            if (normalized.startsWith(MCodeProjectManager.getWorkspaceRoot())) {
                current.path = MCodeProjectManager.relativeName(normalized).replaceAll("(?i)\\.mcode$", "");
            }
            current.dirty = false;
            current.title = chosen.getFileName().toString();
            codeEditor.setCode(content);
            persistSession();
            refreshTabLabels();
            showMessage("Archivo abierto: " + chosen.getFileName());
        } catch (Exception ex) {
            showMessage("Error al abrir archivo: " + ex.getMessage());
        }
    }

    private void saveFile() {
        captureEditor();
        saveTab(tab(), null);
    }

    private void saveTab(Tab current, Runnable afterSave) {
        MCodeSource.Parsed parsed;
        try {
            parsed = MCodeSource.parse(current.code);
        } catch (RuntimeException ex) {
            showMessage("Error en SetPATH: " + ex.getMessage());
            return;
        }
        String path = parsed.path() != null ? parsed.path() : current.path;
        if (path != null && !path.isBlank()) {
            current.path = path;
            try {
                Path target = MCodeProjectManager.normalizeSourcePath(path);
                if (Files.exists(target)) {
                    client.setScreen(new ConfirmScreen(confirmed -> {
                        client.setScreen(this);
                        if (confirmed) writeToPath(current, target, afterSave);
                    }, Text.literal("Sobrescribir archivo"), Text.literal("Ya existe " + path + ".mcode. ¿Deseas sobrescribirlo?")));
                } else writeToPath(current, target, afterSave);
            } catch (Exception ex) {
                showMessage("Error al guardar: " + ex.getMessage());
            }
            return;
        }
        Path chosen = MCodeFileDialogs.chooseSave(MCodeProjectManager.getWorkspaceRoot(), "main.mcode");
        if (chosen == null) {
            String error = MCodeFileDialogs.consumeLastError();
            if (error != null) showMessage("Error al abrir selector: " + error);
            return;
        }
        Path normalized = chosen.toAbsolutePath().normalize();
        if (Files.exists(normalized)) {
            client.setScreen(new ConfirmScreen(confirmed -> {
                client.setScreen(this);
                if (confirmed) writeToChosen(current, normalized, afterSave);
            }, Text.literal("Sobrescribir archivo"), Text.literal("El archivo ya existe. ¿Deseas sobrescribirlo?")));
        } else writeToChosen(current, normalized, afterSave);
    }

    private void writeToPath(Tab targetTab, Path target) { writeToPath(targetTab, target, null); }

    private void writeToPath(Tab targetTab, Path target, Runnable afterSave) {
        try {
            Files.createDirectories(target.getParent());
            String saved = MCodeSource.withPath(targetTab.path, targetTab.code);
            Files.writeString(target, saved, StandardCharsets.UTF_8);
            targetTab.code = saved;
            targetTab.savedCode = saved;
            targetTab.dirty = false;
            if (TABS.indexOf(targetTab) == activeTab && codeEditor != null) codeEditor.setCode(saved);
            persistSession();
            refreshTabLabels();
            showMessage("Guardado en ruta MCODE: " + targetTab.path);
            if (afterSave != null) afterSave.run();
        } catch (Exception ex) {
            showMessage("Error al guardar: " + ex.getMessage());
        }
    }

    private void writeToChosen(Tab targetTab, Path target) { writeToChosen(targetTab, target, null); }

    private void writeToChosen(Tab targetTab, Path target, Runnable afterSave) {
        try {
            Files.createDirectories(target.getParent());
            Files.writeString(target, targetTab.code, StandardCharsets.UTF_8);
            Path root = MCodeProjectManager.getWorkspaceRoot().toAbsolutePath().normalize();
            Path normalized = target.toAbsolutePath().normalize();
            if (normalized.startsWith(root)) targetTab.path = MCodeProjectManager.relativeName(normalized).replaceAll("(?i)\\.mcode$", "");
            targetTab.savedCode = targetTab.code;
            targetTab.dirty = false;
            persistSession();
            refreshTabLabels();
            showMessage("Archivo guardado: " + target.getFileName());
            if (afterSave != null) afterSave.run();
        } catch (Exception ex) {
            showMessage("Error al guardar: " + ex.getMessage());
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        syncState();
        renderBackground(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);
        if (TABS.size() > 5) {
            updateTabScrollbarGeometry();
            context.fill(tabScrollbarX, tabScrollbarY, tabScrollbarX + tabScrollbarWidth, tabScrollbarY + 2, 0xFF353941);
            context.fill(tabScrollbarThumbX, tabScrollbarY - 1, tabScrollbarThumbX + tabScrollbarThumbWidth, tabScrollbarY + 3, draggingTabScrollbar ? 0xFFB9D5FF : 0xFF78A9F5);
        }
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 9, 0xFFFFFF);
        boolean global = ScriptMode.effective() == ScriptMode.Mode.GLOBAL;
        String label = ScriptMode.onRemoteServer() ? "Servidor: solo LOCAL" : (global ? "Modo GLOBAL" : "Modo LOCAL");
        int right = width / 2 + Math.min(700, width - 24) / 2;
        context.drawTextWithShadow(textRenderer, label, right - textRenderer.getWidth(label), 9, global ? 0xFFAA00 : 0x55FF55);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (codeEditor != null && codeEditor.isFocused()) return codeEditor.charTyped(chr, modifiers);
        return super.charTyped(chr, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        boolean ctrl = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
        boolean shift = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;
        if (ctrl && keyCode == GLFW.GLFW_KEY_TAB) { navigateTab(shift ? -1 : 1); return true; }
        if (ctrl && keyCode == GLFW.GLFW_KEY_PAGE_UP) { navigateTab(-1); return true; }
        if (ctrl && keyCode == GLFW.GLFW_KEY_PAGE_DOWN) { navigateTab(1); return true; }
        if (keyCode == GLFW.GLFW_KEY_F5 || (keyCode == GLFW.GLFW_KEY_ENTER && ctrl)) { toggleRun(); return true; }
        if (keyCode == GLFW.GLFW_KEY_S && ctrl) { saveFile(); return true; }
        if (keyCode == GLFW.GLFW_KEY_O && ctrl) { openFile(); return true; }
        if (keyCode == GLFW.GLFW_KEY_W && ctrl) { requestCloseTab(activeTab); return true; }
        if (keyCode == GLFW.GLFW_KEY_C && ctrl && shift) { copyConsoleOutput(); return true; }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) { captureEditor(); persistSession(); }
        if (codeEditor != null && codeEditor.isFocused()) return codeEditor.keyPressed(keyCode, scanCode, modifiers);
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && TABS.size() > 5
                && mouseY >= tabScrollbarY - 1 && mouseY <= tabScrollbarY + 3
                && mouseX >= tabScrollbarX && mouseX <= tabScrollbarX + tabScrollbarWidth) {
            updateTabScrollbarGeometry();
            draggingTabScrollbar = true;
            if (mouseX >= tabScrollbarThumbX && mouseX <= tabScrollbarThumbX + tabScrollbarThumbWidth) {
                tabScrollbarDragOffset = mouseX - tabScrollbarThumbX;
            } else {
                tabScrollbarDragOffset = tabScrollbarThumbWidth / 2.0;
            }
            scrollTabsToPointer(mouseX);
            return true;
        }
        if (codeEditor != null) codeEditor.setFocused(codeEditor.isWithinBounds(mouseX, mouseY));
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (draggingTabScrollbar && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            scrollTabsToPointer(mouseX);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && draggingTabScrollbar) {
            draggingTabScrollbar = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (TABS.size() > 5 && mouseY >= 18 && mouseY <= 45) {
            scrollTabs(verticalAmount < 0 ? 1 : -1);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override public void close() { captureEditor(); persistSession(); super.close(); }
    @Override public void removed() { captureEditor(); persistSession(); super.removed(); }
    @Override public boolean shouldPause() { return false; }
}
