package textmenu.runtime;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;

public final class PlayerSnapshot {

    public record Pose(double x, double y, double z, float yaw, float pitch) {
    }

    private static volatile Pose current;

    private PlayerSnapshot() {
    }

    public static void update(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        if (player == null || client.world == null) {
            current = null;
            return;
        }
        current = new Pose(player.getX(), player.getY(), player.getZ(), player.getYaw(), player.getPitch());
    }

    public static Pose get() {
        return current;
    }
}
