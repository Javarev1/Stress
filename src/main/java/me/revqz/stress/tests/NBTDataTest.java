package me.revqz.stress.tests;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.block.ShulkerBox;

import net.kyori.adventure.text.Component;
import me.revqz.stress.Stress;
import me.revqz.stress.test.Test;

public class NBTDataTest implements Test {

    private static final int PAGES = 100;
    private static final int CHARS_PER_PAGE = 256;
    private static final int NESTING_DEPTH = 5;
    private static final int ITEMS_PER_TICK = 3;

    private static final Random RNG = new Random();

    private BukkitTask task;
    private final List<UUID> trackedPlayers = new ArrayList<>();

    @Override
    public void start() {
        task = Bukkit.getScheduler().runTaskTimer(Stress.get(), () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (!trackedPlayers.contains(player.getUniqueId()))
                    trackedPlayers.add(player.getUniqueId());

                for (int i = 0; i < ITEMS_PER_TICK; i++) {
                    ItemStack item = RNG.nextBoolean()
                            ? buildMassiveBook()
                            : buildNestedShulker(NESTING_DEPTH);

                    if (player.getInventory().firstEmpty() != -1)
                        player.getInventory().addItem(item);
                }
            }
        }, 0L, 20L);
    }

    @Override
    public void stop() {
        if (task != null)
            task.cancel();

        Bukkit.getScheduler().runTask(Stress.get(), () -> {
            for (UUID id : trackedPlayers) {
                Player player = Bukkit.getPlayer(id);
                if (player == null)
                    continue;

                removeTrackedItems(player.getInventory());
                player.updateInventory();
            }
            trackedPlayers.clear();
        });
    }

    @Override
    public String getName() {
        return "nbt-data";
    }

    private ItemStack buildMassiveBook() {
        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) book.getItemMeta();

        meta.setTitle("NBT Stress");
        meta.setAuthor("NBT Stress Author");

        for (int i = 0; i < PAGES; i++) {
            StringBuilder sb = new StringBuilder(CHARS_PER_PAGE);
            for (int c = 0; c < CHARS_PER_PAGE; c++) {
                sb.append((char) (0x4E00 + RNG.nextInt(0x9FFF - 0x4E00)));
            }
            meta.addPages(Component.text(sb.toString()));
        }

        book.setItemMeta(meta);
        return book;
    }

    private ItemStack buildNestedShulker(int depth) {
        if (depth <= 0) {
            ItemStack base = new ItemStack(Material.DIAMOND, 64);
            return base;
        }

        ItemStack shulker = new ItemStack(Material.SHULKER_BOX);
        BlockStateMeta meta = (BlockStateMeta) shulker.getItemMeta();
        ShulkerBox box = (ShulkerBox) meta.getBlockState();

        ItemStack inner = buildNestedShulker(depth - 1);
        box.getInventory().setItem(0, inner);
        box.getInventory().setItem(13, buildMassiveBook());

        meta.setBlockState(box);
        meta.displayName(Component.text("Depth-" + depth));
        shulker.setItemMeta(meta);

        return shulker;
    }

    private void removeTrackedItems(Inventory inv) {
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack item = inv.getItem(i);
            if (item == null)
                continue;

            if (isTrackedItem(item))
                inv.setItem(i, null);
        }
    }

    private boolean isTrackedItem(ItemStack item) {
        if (item.getType() == Material.WRITTEN_BOOK)
            return true;

        if (item.getType() == Material.SHULKER_BOX)
            return true;

        return false;
    }
}
