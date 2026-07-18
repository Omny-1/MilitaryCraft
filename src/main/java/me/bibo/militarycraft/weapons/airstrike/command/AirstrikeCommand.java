package me.bibo.militarycraft.weapons.airstrike.command;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.StringUtil;
import org.bukkit.util.Vector;
import me.bibo.militarycraft.weapons.airstrike.AirstrikeRuntime;
import me.bibo.militarycraft.core.command.SubCommand;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AirstrikeCommand implements CommandExecutor, TabCompleter {

    /** Keeps targeting inside the vanilla world border. */
    private static final double WORLD_LIMIT = 2.9999984E7;
    private static final List<String> ROOT_SUBS = List.of("call", "item", "give", "reload");

    private final AirstrikeRuntime plugin;
    private final NamespacedKey beaconKey;

    public AirstrikeCommand(AirstrikeRuntime plugin) {
        this.plugin = plugin;
        this.beaconKey = beaconKey(plugin);
    }

    // ----- beacon item identity (PersistentDataContainer, not a lore tag) -----

    public static NamespacedKey beaconKey(AirstrikeRuntime plugin) {
        return new NamespacedKey("airstrikeplugin", "airstrike_beacon");
    }

    public static ItemStack createAirstrikeItem(AirstrikeRuntime plugin) {
        ItemStack item = new ItemStack(Material.FIREWORK_ROCKET);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Airstrike Beacon", NamedTextColor.RED)
                .decoration(TextDecoration.ITALIC, false)
                .decorate(TextDecoration.BOLD));
        meta.lore(List.of(
                Component.text("Right-click any surface - call an airstrike.", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("A Su-57 fighter will strike the target.", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false)));
        meta.setEnchantmentGlintOverride(true);
        // 3D model from the WarKit resource pack (warkit:airstrike_beacon) instead of a rocket-like item.
        meta.setItemModel(new NamespacedKey("warkit", "airstrike_beacon"));
        meta.getPersistentDataContainer().set(beaconKey(plugin), PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    public static boolean isAirstrikeItem(AirstrikeRuntime plugin, ItemStack item) {
        if (item == null || item.getType() != Material.FIREWORK_ROCKET || !item.hasItemMeta()) {
            return false;
        }
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        return pdc.has(beaconKey(plugin), PersistentDataType.BYTE);
    }

    // ----- target resolution (shared by command + listener) -----

    public static Location resolveTarget(Player player, int maxDist) {
        Location eye = player.getEyeLocation();
        World world = player.getWorld();
        RayTraceResult result = world.rayTraceBlocks(
                eye, eye.getDirection(), maxDist, FluidCollisionMode.NEVER, true);
        if (result != null && result.getHitBlock() != null) {
            return result.getHitBlock().getLocation().add(0.5, 0.0, 0.5);
        }
        // Nothing hit within range: project forward, then snap down to the surface.
        Vector dir = eye.getDirection().normalize();
        Location projected = eye.clone().add(dir.multiply(maxDist));
        int blockX = (int) Math.floor(clamp(projected.getX()));
        int blockZ = (int) Math.floor(clamp(projected.getZ()));
        int groundY = world.getHighestBlockYAt(blockX, blockZ);
        return new Location(world, blockX + 0.5, groundY, blockZ + 0.5);
    }

    private static double clamp(double v) {
        return Math.max(-WORLD_LIMIT, Math.min(WORLD_LIMIT, v));
    }

    // ----- command -----

    public List<SubCommand> all() {
        return ROOT_SUBS.stream().map(RootSub::new).map(SubCommand.class::cast).toList();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.message("players-only"));
            return true;
        }
        if (!player.hasPermission("airstrike.use")) {
            player.sendMessage(plugin.message("no-permission"));
            return true;
        }
        if (args.length == 0) {
            int maxDist = plugin.getConfig().getInt("max-target-distance", 150);
            Location target = resolveTarget(player, maxDist);
            plugin.getAirstrikeManager().callAirstrike(player, target);
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "item", "give" -> {
                if (!player.hasPermission("airstrike.give")) {
                    player.sendMessage(plugin.message("no-permission"));
                    return true;
                }
                player.getInventory().addItem(createAirstrikeItem(plugin));
                player.sendMessage(plugin.message("item-given"));
            }
            case "reload" -> {
                if (!player.hasPermission("airstrike.reload")) {
                    player.sendMessage(plugin.message("no-permission"));
                    return true;
                }
                plugin.bukkitPlugin().reloadAll();
                player.sendMessage(plugin.message("reloaded"));
            }
            default -> sendHelp(player);
        }
        return true;
    }

    private void sendHelp(Player player) {
        player.sendMessage(AirstrikeRuntime.text("&6&l✈ AirstrikePlugin &7- commands:"));
        player.sendMessage(AirstrikeRuntime.text("&e/airstrike &7- airstrike at your crosshair target"));
        if (player.hasPermission("airstrike.give")) {
            player.sendMessage(AirstrikeRuntime.text("&e/airstrike item &7- get an airstrike beacon"));
        }
        if (player.hasPermission("airstrike.reload")) {
            player.sendMessage(AirstrikeRuntime.text("&e/airstrike reload &7- reload config"));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> options = new ArrayList<>();
            if (sender.hasPermission("airstrike.give")) {
                options.add("item");
                options.add("give");
            }
            if (sender.hasPermission("airstrike.reload")) {
                options.add("reload");
            }
            return StringUtil.copyPartialMatches(args[0], options, new ArrayList<>());
        }
        return Collections.emptyList();
    }

    private static String permissionFor(String sub) {
        return switch (sub) {
            case "item", "give" -> "airstrike.give";
            case "reload" -> "airstrike.reload";
            default -> "airstrike.use";
        };
    }

    private void executeSub(CommandSender sender, String sub, String[] args) {
        if (sub.equals("call")) {
            onCommand(sender, null, "mc", args);
            return;
        }
        String[] shifted = new String[args.length + 1];
        shifted[0] = sub;
        System.arraycopy(args, 0, shifted, 1, args.length);
        onCommand(sender, null, "mc", shifted);
    }

    private List<String> tabSub(CommandSender sender, String sub, String[] args) {
        if (sub.equals("call")) {
            return List.of();
        }
        String[] shifted = new String[args.length + 1];
        shifted[0] = sub;
        System.arraycopy(args, 0, shifted, 1, args.length);
        return onTabComplete(sender, null, "mc", shifted);
    }

    private final class RootSub implements SubCommand {

        private final String name;

        private RootSub(String name) {
            this.name = name;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public String permission() {
            return permissionFor(name);
        }

        @Override
        public void execute(CommandSender sender, String[] args) {
            executeSub(sender, name, args);
        }

        @Override
        public List<String> tabComplete(CommandSender sender, String[] args) {
            return tabSub(sender, name, args);
        }
    }
}
