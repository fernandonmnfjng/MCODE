package textmenu.draw;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderPhase;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;

import java.util.OptionalDouble;
import java.util.concurrent.ConcurrentHashMap;

final class RenderLayers {

    private static final int BUFFER_SIZE = 1536;
    private static final int MAX_LINE_LAYERS = 96;

    private static final ConcurrentHashMap<Integer, RenderLayer> LINES = new ConcurrentHashMap<>();
    private static RenderLayer quads;

    private RenderLayers() {
    }

    static int lineKey(float widthPixels) {
        return Math.max(1, Math.round(widthPixels * 2.0f));
    }

    static RenderLayer lines(int key) {
        RenderLayer cached = LINES.get(key);
        if (cached != null) return cached;
        if (LINES.size() >= MAX_LINE_LAYERS) return RenderLayer.getLines();
        RenderLayer created = buildLines(key);
        LINES.put(key, created);
        return created;
    }

    static RenderLayer quads() {
        if (quads == null) quads = buildQuads();
        return quads;
    }

    private static RenderLayer buildLines(int key) {
        RenderLayer.MultiPhaseParameters parameters = RenderLayer.MultiPhaseParameters.builder()
                .program(RenderPhase.LINES_PROGRAM)
                .lineWidth(new RenderPhase.LineWidth(OptionalDouble.of(key / 2.0)))
                .layering(RenderPhase.VIEW_OFFSET_Z_LAYERING)
                .transparency(RenderPhase.TRANSLUCENT_TRANSPARENCY)
                .target(RenderPhase.MAIN_TARGET)
                .writeMaskState(RenderPhase.ALL_MASK)
                .cull(RenderPhase.DISABLE_CULLING)
                .build(false);
        return RenderLayer.of("mcode_lines_" + key, VertexFormats.LINES, VertexFormat.DrawMode.LINES,
                BUFFER_SIZE, parameters);
    }

    private static RenderLayer buildQuads() {
        RenderLayer.MultiPhaseParameters parameters = RenderLayer.MultiPhaseParameters.builder()
                .program(RenderPhase.COLOR_PROGRAM)
                .transparency(RenderPhase.TRANSLUCENT_TRANSPARENCY)
                .target(RenderPhase.MAIN_TARGET)
                .writeMaskState(RenderPhase.COLOR_MASK)
                .cull(RenderPhase.DISABLE_CULLING)
                .build(false);
        return RenderLayer.of("mcode_quads", VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.QUADS,
                BUFFER_SIZE, false, true, parameters);
    }
}
