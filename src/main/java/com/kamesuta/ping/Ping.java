package com.kamesuta.ping;

import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class Ping extends JavaPlugin implements Listener {
    static Logger LOGGER;
    private final NamespacedKey pingOwnerKey = Objects.requireNonNull(NamespacedKey.fromString("ping_owner", this));
    private final NamespacedKey pingItemKey = Objects.requireNonNull(NamespacedKey.fromString("ping_item", this));
    private final NamespacedKey pingNameKey = Objects.requireNonNull(NamespacedKey.fromString("ping_name", this));

    @Override
    public void onEnable() {
        // Plugin startup logic
        LOGGER = getLogger();
        getServer().getPluginManager().registerEvents(this, this);
    }

    @EventHandler
    public void onClick(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!player.hasPermission("ping")) {
            // 権限がないプレイヤーは無視

        }

        ItemStack item = event.getPlayer().getInventory().getItemInMainHand();
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) {
            // 名前がないアイテムは無視
            return;
        }

        String text = meta.getDisplayName();
        if (!text.startsWith("!")) {
            // !で始まらないアイテムは無視
            return;
        }
        String itemName = text.substring(1);

        // 右クリックキャンセル
        event.setCancelled(true);

        RayTraceResult result = player.rayTraceBlocks(50, FluidCollisionMode.NEVER);
        if (result == null) {
            // 右クリック位置が見つからない場合は無視
            return;
        }

        // 右クリック位置を取得
        World world = player.getWorld();
        Location location = result.getHitPosition().toLocation(world);

        if (event.getAction() == Action.RIGHT_CLICK_AIR
                || event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            // 右クリックの場合
            // マーカーを表示
            List<Entity> removeEntity = new ArrayList<>();

            // マーカーの名前を表示
            ArmorStand armorStand = world.spawn(location, ArmorStand.class);
            removeEntity.add(armorStand);
            armorStand.setCustomNameVisible(true);
            armorStand.setCustomName(itemName);
            armorStand.setGravity(false);
            armorStand.setVisible(false);
            armorStand.setInvulnerable(true);
            armorStand.setSilent(true);
            armorStand.setCollidable(false);
            armorStand.setSmall(true);
            armorStand.addScoreboardTag("ping");
            setOwnerData(armorStand, player, item, itemName);

            if (item.getType().isBlock()) {
                FallingBlock blockEntity = world.spawnFallingBlock(location, item.getType().createBlockData());
                removeEntity.add(blockEntity);
                setTickLived(blockEntity, -100000);
                blockEntity.setGravity(false);
                blockEntity.setDropItem(false);
                blockEntity.setInvulnerable(true);
                blockEntity.setGlowing(true);
                blockEntity.setSilent(true);
                blockEntity.addScoreboardTag("ping");
                setOwnerData(blockEntity, player, item, itemName);

                blockEntity.addPassenger(armorStand);
            } else {
                Item itemEntity = world.dropItem(location, item);
                removeEntity.add(itemEntity);
                itemEntity.setInvulnerable(true);
                itemEntity.setGlowing(true);
                itemEntity.setSilent(true);
                itemEntity.setPickupDelay(Integer.MAX_VALUE);
                itemEntity.setTicksLived(Integer.MAX_VALUE);
                itemEntity.setCanPlayerPickup(false);
                itemEntity.addScoreboardTag("ping");
                setOwnerData(itemEntity, player, item, itemName);

                armorStand.addPassenger(itemEntity);
            }

            // 一定時間後に削除
            new BukkitRunnable() {
                @Override
                public void run() {
                    removeEntity.forEach(Entity::remove);
                }
            }.runTaskLater(this, 20 * 60 * 5);
        } else {
            // 左クリックの場合
            // 近くのピンを削除
            location.getNearbyEntities(3, 3, 3).stream()
                    .filter(entity -> entity.getScoreboardTags().contains("ping"))
                    .filter(entity -> isOwnerDataMatched(entity, player, item, itemName))
                    .forEach(Entity::remove);
        }
    }

    private void setOwnerData(Entity entity, Player player, ItemStack item, String itemName) {
        PersistentDataContainer data = entity.getPersistentDataContainer();
        data.set(pingOwnerKey, PersistentDataType.STRING, player.getName());
        data.set(pingItemKey, PersistentDataType.STRING, item.getType().name());
        data.set(pingNameKey, PersistentDataType.STRING, itemName);
    }

    private boolean isOwnerDataMatched(Entity entity, Player player, ItemStack item, String itemName) {
        PersistentDataContainer data = entity.getPersistentDataContainer();
        return Objects.equals(player.getName(), data.get(pingOwnerKey, PersistentDataType.STRING))
                && Objects.equals(item.getType().name(), data.get(pingItemKey, PersistentDataType.STRING))
                && Objects.equals(itemName, data.get(pingNameKey, PersistentDataType.STRING));
    }

    /**
     * ブロックのTickLivedを設定する (負数対応)
     *
     * @param blockEntity エンティティ
     */
    private void setTickLived(FallingBlock blockEntity, int tickLived) {
        try {
            Object handle = blockEntity.getClass().getMethod("getHandle").invoke(blockEntity);
            handle.getClass().getField("ticksLived").set(handle, tickLived);
        } catch (ReflectiveOperationException e) {
            LOGGER.log(Level.WARNING, "Failed to set tick lived", e);
        }
    }
}
