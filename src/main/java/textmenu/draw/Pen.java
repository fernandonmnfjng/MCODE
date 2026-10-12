package textmenu.draw;

import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import textmenu.runtime.PlayerSnapshot;

final class Pen {

    private final MatrixStack.Entry entry;
    private final double camX;
    private final double camY;
    private final double camZ;
    private final double[] transformed = new double[3];
    private final double[] shapePoint = new double[3];
    private VertexConsumer buffer;
    private Shape shape;
    private boolean transformLocal;
    private double originX;
    private double originY;
    private double originZ;
    private double cosYaw = 1.0;
    private double sinYaw;
    private double cosPitch = 1.0;
    private double sinPitch;
    private int red;
    private int green;
    private int blue;
    private int alpha;

    Pen(MatrixStack.Entry entry, double camX, double camY, double camZ) {
        this.entry = entry;
        this.camX = camX;
        this.camY = camY;
        this.camZ = camZ;
    }

    void begin(VertexConsumer buffer) {
        this.buffer = buffer;
    }

    void shape(Shape shape) {
        this.shape = shape;
        transformLocal = false;
        if (shape == null || shape.space() != Space.LOCAL) return;

        PlayerSnapshot.Pose pose = shape.followPlayer() ? PlayerSnapshot.get() : shape.anchorPose();
        if (pose == null) pose = shape.anchorPose();
        if (pose == null) return;

        double yaw = Math.toRadians(pose.yaw());
        double pitch = Math.toRadians(pose.pitch());
        originX = pose.x();
        originY = pose.y();
        originZ = pose.z();
        cosYaw = Math.cos(yaw);
        sinYaw = Math.sin(yaw);
        cosPitch = Math.cos(pitch);
        sinPitch = Math.sin(pitch);
        transformLocal = true;
    }

    void color(int rgb, float opacity) {
        red = (rgb >> 16) & 0xFF;
        green = (rgb >> 8) & 0xFF;
        blue = rgb & 0xFF;
        alpha = Math.round(Math.max(0f, Math.min(1f, opacity)) * 255f);
    }

    void line(double x1, double y1, double z1, double x2, double y2, double z2) {
        transformToCameraSpace(x1, y1, z1);
        float ax = (float) transformed[0];
        float ay = (float) transformed[1];
        float az = (float) transformed[2];
        transformToCameraSpace(x2, y2, z2);
        float bx = (float) transformed[0];
        float by = (float) transformed[1];
        float bz = (float) transformed[2];

        float dx = bx - ax;
        float dy = by - ay;
        float dz = bz - az;
        float length = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length < 1.0e-6f) return;
        dx /= length;
        dy /= length;
        dz /= length;
        buffer.vertex(entry, ax, ay, az).color(red, green, blue, alpha).normal(entry, dx, dy, dz);
        buffer.vertex(entry, bx, by, bz).color(red, green, blue, alpha).normal(entry, dx, dy, dz);
    }

    void quad(double[] p, int a, int b, int c, int d) {
        vertex(p, a);
        vertex(p, b);
        vertex(p, c);
        vertex(p, d);
    }

    private void vertex(double[] p, int index) {
        int offset = index * 3;
        transformToCameraSpace(p[offset], p[offset + 1], p[offset + 2]);
        buffer.vertex(entry, (float) transformed[0], (float) transformed[1], (float) transformed[2])
                .color(red, green, blue, alpha);
    }

    private void transformToCameraSpace(double x, double y, double z) {
        if (shape != null) {
            shape.transformPoint(x, y, z, shapePoint);
            x = shapePoint[0]; y = shapePoint[1]; z = shapePoint[2];
        }
        if (transformLocal) {
            double rotatedY = y * cosPitch - z * sinPitch;
            double rotatedZ = y * sinPitch + z * cosPitch;
            double worldX = originX - x * cosYaw - rotatedZ * sinYaw;
            double worldY = originY + rotatedY;
            double worldZ = originZ - x * sinYaw + rotatedZ * cosYaw;
            transformed[0] = worldX - camX;
            transformed[1] = worldY - camY;
            transformed[2] = worldZ - camZ;
        } else {
            transformed[0] = x - camX;
            transformed[1] = y - camY;
            transformed[2] = z - camZ;
        }
    }
}
