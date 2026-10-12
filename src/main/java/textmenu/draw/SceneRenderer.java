package textmenu.draw;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;
import textmenu.runtime.PlayerSnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public final class SceneRenderer {

    private static final float REFERENCE_WIDTH = 1920.0f;
    private static final double MAX_RENDER_DISTANCE_SQUARED = 256.0 * 256.0;

    private SceneRenderer() {
    }

    public static void register() {
        WorldRenderEvents.AFTER_TRANSLUCENT.register(SceneRenderer::render);
    }

    private static void render(WorldRenderContext context) {
        Scene scene = Scene.global();
        if (scene.size() == 0) return;
        if (!(context.consumers() instanceof VertexConsumerProvider.Immediate immediate)) return;
        MatrixStack stack = context.matrixStack();
        if (stack == null) return;

        Vec3d camera = context.camera().getPos();
        Pen pen = new Pen(stack.peek(), camera.x, camera.y, camera.z);

        float scale = Math.max(1.0f,
                MinecraftClient.getInstance().getWindow().getFramebufferWidth() / REFERENCE_WIDTH);

        List<Shape> fills = new ArrayList<>();
        Map<Integer, List<Shape>> lines = new TreeMap<>();
        // Reuse scratch buffers for every shape in this render pass to avoid
        // allocating two small arrays per shape per frame.
        double[] centerBuffer = new double[3];
        double[] transformedBuffer = new double[3];
        for (Shape shape : scene.shapes()) {
            if (!shape.visible() || outsideRenderDistance(shape, camera, centerBuffer, transformedBuffer)) continue;
            if (shape.filled()) fills.add(shape);
            int key = RenderLayers.lineKey(shape.thickness() * scale);
            lines.computeIfAbsent(key, k -> new ArrayList<>()).add(shape);
        }

        if (!fills.isEmpty()) {
            RenderLayer layer = RenderLayers.quads();
            pen.begin(immediate.getBuffer(layer));
            for (Shape shape : fills) {
                pen.shape(shape);
                pen.color(shape.rgb(), shape.alpha());
                fill(pen, shape);
            }
            immediate.draw(layer);
        }

        for (Map.Entry<Integer, List<Shape>> group : lines.entrySet()) {
            RenderLayer layer = RenderLayers.lines(group.getKey());
            pen.begin(immediate.getBuffer(layer));
            for (Shape shape : group.getValue()) {
                pen.shape(shape);
                pen.color(shape.rgb(), shape.alpha());
                outline(pen, shape);
            }
            immediate.draw(layer);
        }
    }


    private static boolean outsideRenderDistance(Shape shape, Vec3d camera, double[] center, double[] transformed) {
        shape.pivotPosition(center);
        shape.transformPoint(center[0], center[1], center[2], transformed);
        double x = transformed[0], y = transformed[1], z = transformed[2];
        if (shape.space() == Space.LOCAL) {
            PlayerSnapshot.Pose pose = shape.followPlayer() ? PlayerSnapshot.get() : shape.anchorPose();
            if (pose == null) pose = shape.anchorPose();
            if (pose == null) return true;
            double yaw = Math.toRadians(pose.yaw()), pitch = Math.toRadians(pose.pitch());
            double rotatedY = y * Math.cos(pitch) - z * Math.sin(pitch);
            double rotatedZ = y * Math.sin(pitch) + z * Math.cos(pitch);
            double worldX = pose.x() - x * Math.cos(yaw) - rotatedZ * Math.sin(yaw);
            double worldY = pose.y() + rotatedY;
            double worldZ = pose.z() - x * Math.sin(yaw) + rotatedZ * Math.cos(yaw);
            x = worldX; y = worldY; z = worldZ;
        }
        double dx = x - camera.x, dy = y - camera.y, dz = z - camera.z;
        return dx * dx + dy * dy + dz * dz > MAX_RENDER_DISTANCE_SQUARED;
    }

    private static void outline(Pen pen, Shape shape) {
        switch (shape) {
            case LineShape line -> {
                double[] p = line.points();
                for (int i = 0; i + 5 < p.length; i += 3) {
                    pen.line(p[i], p[i + 1], p[i + 2], p[i + 3], p[i + 4], p[i + 5]);
                }
            }
            case BoxShape box -> boxEdges(pen, box.bounds());
            case QuadShape quad -> loop(pen, quad.corners());
            case TexturedQuadShape textured -> loop(pen, textured.corners());
            case PolygonShape polygon -> polygonOutline(pen, polygon);
            case MeshShape mesh -> {
                double[] v = mesh.vertices();
                int[] edges = mesh.edges();
                for (int i = 0; i + 1 < edges.length; i += 2) edge(pen, v, edges[i], edges[i + 1]);
            }
        }
    }

    private static void fill(Pen pen, Shape shape) {
        switch (shape) {
            case LineShape ignored -> {
            }
            case BoxShape box -> boxFaces(pen, box.bounds());
            case QuadShape quad -> pen.quad(quad.corners(), 0, 1, 2, 3);
            case TexturedQuadShape textured -> pen.quad(textured.corners(), 0, 1, 2, 3);
            case PolygonShape polygon -> {
                double[] v = polygon.vertices();
                int[] t = polygon.triangles();
                for (int i = 0; i < t.length; i += 3) {
                    pen.quad(v, t[i], t[i + 1], t[i + 2], t[i + 2]);
                }
            }
            case MeshShape mesh -> {
                double[] v = mesh.vertices();
                int[] t = mesh.triangles();
                for (int i = 0; i < t.length; i += 3) pen.quad(v, t[i], t[i + 1], t[i + 2], t[i + 2]);
            }
        }
    }

    private static void polygonOutline(Pen pen, PolygonShape polygon) {
        double[] vertices = polygon.vertices();
        int[] boundary = polygon.boundary();
        for (int i = 0; i < boundary.length; i++) {
            edge(pen, vertices, boundary[i], boundary[(i + 1) % boundary.length]);
        }
    }

    private static void edge(Pen pen, double[] v, int a, int b) {
        pen.line(v[a * 3], v[a * 3 + 1], v[a * 3 + 2], v[b * 3], v[b * 3 + 1], v[b * 3 + 2]);
    }

    private static void loop(Pen pen, double[] c) {
        for (int i = 0; i < 4; i++) edge(pen, c, i, (i + 1) % 4);
    }

    private static void boxEdges(Pen pen, double[] b) {
        double[] c = boxCorners(b);
        int[][] edges = {
                {0, 1}, {1, 2}, {2, 3}, {3, 0},
                {4, 5}, {5, 6}, {6, 7}, {7, 4},
                {0, 4}, {1, 5}, {2, 6}, {3, 7}
        };
        for (int[] e : edges) edge(pen, c, e[0], e[1]);
    }

    private static void boxFaces(Pen pen, double[] b) {
        double[] c = boxCorners(b);
        pen.quad(c, 0, 1, 2, 3);
        pen.quad(c, 4, 5, 6, 7);
        pen.quad(c, 0, 1, 5, 4);
        pen.quad(c, 3, 2, 6, 7);
        pen.quad(c, 0, 3, 7, 4);
        pen.quad(c, 1, 2, 6, 5);
    }

    private static double[] boxCorners(double[] b) {
        return new double[]{
                b[0], b[1], b[2],
                b[3], b[1], b[2],
                b[3], b[1], b[5],
                b[0], b[1], b[5],
                b[0], b[4], b[2],
                b[3], b[4], b[2],
                b[3], b[4], b[5],
                b[0], b[4], b[5]
        };
    }
}
