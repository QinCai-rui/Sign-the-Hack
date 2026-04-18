package xyz.qincai.signthehack.util;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public final class SignProbeTransport {

    private SignProbeTransport() {
    }

    public static Location findAirProbeLocation(Player player) {
        Location base = player.getLocation().clone();
        for (int dy = 1; dy <= 5; dy++) {
            Location candidate = base.clone().add(0, dy, 0);
            if (candidate.getBlock().getType().isAir()) {
                return candidate;
            }
        }

        int[][] offsets = {
                {1, 1, 0}, {-1, 1, 0}, {0, 1, 1}, {0, 1, -1},
                {1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1},
                {2, 1, 0}, {-2, 1, 0}, {0, 1, 2}, {0, 1, -2}
        };
        for (int[] offset : offsets) {
            Location candidate = base.clone().add(offset[0], offset[1], offset[2]);
            if (candidate.getBlock().getType().isAir()) {
                return candidate;
            }
        }
        return null;
    }

    public static void setAllowedEditor(Location location, UUID playerUuid, Plugin plugin) {
        try {
            Object worldHandle = location.getWorld().getClass().getMethod("getHandle").invoke(location.getWorld());
            Class<?> blockPosClass = Class.forName("net.minecraft.core.BlockPos");
            Object blockPos = blockPosClass.getConstructor(int.class, int.class, int.class)
                    .newInstance(location.getBlockX(), location.getBlockY(), location.getBlockZ());

            Method getBlockEntity = Arrays.stream(worldHandle.getClass().getMethods())
                    .filter(method -> method.getName().equals("getBlockEntity") && method.getParameterCount() == 1)
                    .findFirst()
                    .orElse(null);
            if (getBlockEntity == null) {
                return;
            }

            Object blockEntity = getBlockEntity.invoke(worldHandle, blockPos);
            if (blockEntity == null) {
                return;
            }

            for (Method method : blockEntity.getClass().getMethods()) {
                if (method.getName().equals("setAllowedPlayerEditor") && method.getParameterCount() == 1) {
                    method.invoke(blockEntity, playerUuid);
                    return;
                }
            }

            for (Field field : allFields(blockEntity.getClass())) {
                if (field.getType().equals(UUID.class)) {
                    field.setAccessible(true);
                    field.set(blockEntity, playerUuid);
                    return;
                }
            }
        } catch (Exception exception) {
            plugin.getLogger().warning("setAllowedEditor failed: " + exception.getMessage());
        }
    }

    public static void sendBlockEntityUpdate(Player player, Location location, Plugin plugin) {
        try {
            Object worldHandle = location.getWorld().getClass().getMethod("getHandle").invoke(location.getWorld());
            Class<?> blockPosClass = Class.forName("net.minecraft.core.BlockPos");
            Object blockPos = blockPosClass.getConstructor(int.class, int.class, int.class)
                    .newInstance(location.getBlockX(), location.getBlockY(), location.getBlockZ());

            Method getBlockEntity = Arrays.stream(worldHandle.getClass().getMethods())
                    .filter(method -> method.getName().equals("getBlockEntity") && method.getParameterCount() == 1)
                    .findFirst()
                    .orElse(null);
            if (getBlockEntity == null) {
                return;
            }

            Object blockEntity = getBlockEntity.invoke(worldHandle, blockPos);
            if (blockEntity == null) {
                return;
            }

            Class<?> packetClass = Class.forName("net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket");
            Method create = Arrays.stream(packetClass.getMethods())
                    .filter(method -> method.getName().equals("create") && method.getParameterCount() == 1)
                    .findFirst()
                    .orElse(null);
            if (create == null) {
                return;
            }

            Object packet = create.invoke(null, blockEntity);
            sendPacket(player, packet, plugin);
        } catch (Exception exception) {
            plugin.getLogger().warning("sendBlockEntityUpdate failed: " + exception.getMessage());
        }
    }

    public static void sendOpenSignEditor(Player player, Location location, Plugin plugin) {
        try {
            Class<?> blockPosClass = Class.forName("net.minecraft.core.BlockPos");
            Class<?> packetClass = Class.forName("net.minecraft.network.protocol.game.ClientboundOpenSignEditorPacket");
            Object blockPos = blockPosClass.getConstructor(int.class, int.class, int.class)
                    .newInstance(location.getBlockX(), location.getBlockY(), location.getBlockZ());
            Object packet = packetClass.getConstructor(blockPosClass, boolean.class).newInstance(blockPos, true);
            sendPacket(player, packet, plugin);
        } catch (Exception exception) {
            plugin.getLogger().warning("sendOpenSignEditor failed: " + exception.getMessage());
        }
    }

    private static void sendPacket(Player player, Object packet, Plugin plugin) {
        try {
            Object handle = player.getClass().getMethod("getHandle").invoke(player);
            Object connection = null;

            for (String fieldName : new String[]{"connection", "networkManager", "playerConnection"}) {
                try {
                    Field field;
                    try {
                        field = handle.getClass().getField(fieldName);
                    } catch (NoSuchFieldException ignored) {
                        field = handle.getClass().getDeclaredField(fieldName);
                        field.setAccessible(true);
                    }
                    Object value = field.get(handle);
                    if (value != null) {
                        connection = value;
                        break;
                    }
                } catch (Exception ignored) {
                }
            }

            if (connection == null) {
                throw new IllegalStateException("player connection not found");
            }

            Method sendMethod = null;
            for (Method method : connection.getClass().getMethods()) {
                if (method.getName().equals("send") && method.getParameterCount() == 1) {
                    sendMethod = method;
                    break;
                }
            }

            if (sendMethod == null) {
                throw new IllegalStateException("connection send method not found");
            }

            sendMethod.invoke(connection, packet);
        } catch (Exception exception) {
            plugin.getLogger().warning("sendPacket failed: " + exception.getMessage());
        }
    }

    private static List<Field> allFields(Class<?> type) {
        List<Field> fields = new ArrayList<>();
        Class<?> current = type;
        while (current != null && current != Object.class) {
            fields.addAll(Arrays.asList(current.getDeclaredFields()));
            current = current.getSuperclass();
        }
        return fields;
    }
}
