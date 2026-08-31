package me.bibo.militarycraft.vehicles.pickup.commands;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import me.bibo.militarycraft.core.command.SubCommand;
import me.bibo.militarycraft.vehicles.pickup.PickupRuntime;
import me.bibo.militarycraft.vehicles.pickup.items.PickupItem;
import me.bibo.militarycraft.vehicles.pickup.vehicle.Pickup;
import me.bibo.militarycraft.vehicles.pickup.vehicle.PickupCollision;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

/**
 * {@code /pickup}: hand out the item, spawn or remove a vehicle, list what is loaded, sweep up
 * leftovers, reload the config. Each subcommand carries its own permission.
 */
public final class PickupCommand
implements TabExecutor {
    private static final List<String> ROOT_SUBS =
            List.of("give", "spawn", "place", "remove", "cleanup", "list", "reload", "migrate");
    private final PickupRuntime plugin;

    public PickupCommand(PickupRuntime plugin) {
        this.plugin = plugin;
    }

    public List<SubCommand> all() {
        return ROOT_SUBS.stream().map(RootSub::new).map(SubCommand.class::cast).toList();
    }

    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            this.msg(sender, "&a/pickup give|spawn|place|remove|cleanup|list|reload|migrate");
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "give": {
                this.give(sender, args);
                break;
            }
            case "spawn": {
                this.spawn(sender);
                break;
            }
            case "place": {
                this.place(sender, args);
                break;
            }
            case "remove": {
                this.remove(sender);
                break;
            }
            case "cleanup": 
            case "purge": 
            case "clear": {
                this.cleanup(sender);
                break;
            }
            case "list": {
                this.msg(sender, "&aActive pickups: &f" + this.plugin.pickups().count());
                break;
            }
            case "reload": {
                this.reload(sender);
                break;
            }
            case "migrate": {
                this.migrate(sender);
                break;
            }
            default: {
                this.msg(sender, "&cUnknown subcommand.");
            }
        }
        return true;
    }

    private void give(CommandSender sender, String[] args) {
        HashMap<Integer, ItemStack> leftovers;
        Player target;
        if (!sender.hasPermission("pickupcraft.give")) {
            this.msg(sender, "&cYou do not have permission.");
            return;
        }
        if (args.length >= 2) {
            target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                this.msg(sender, "&cPlayer not found.");
                return;
            }
        } else if (sender instanceof Player) {
            Player p;
            target = p = (Player)sender;
        } else {
            this.msg(sender, "&cSpecify a player: /pickup give <player>");
            return;
        }
        if (!(leftovers = new HashMap<>(target.getInventory().addItem(new ItemStack[]{PickupItem.create(this.plugin)}))).isEmpty()) {
            leftovers.values().forEach(item -> target.getWorld().dropItemNaturally(target.getLocation(), item));
            this.msg(sender, "&eThe player's inventory is full: the Pickup item was dropped near &f" + target.getName());
            return;
        }
        this.msg(sender, "&aGave the Pickup item to &f" + target.getName());
    }

    private void spawn(CommandSender sender) {
        if (!(sender instanceof Player)) {
            this.msg(sender, "&cPlayers only.");
            return;
        }
        Player player = (Player)sender;
        if (!sender.hasPermission("pickupcraft.spawn")) {
            this.msg(sender, "&cYou do not have permission.");
            return;
        }
        Location at = this.findSpawnAnchor(player);
        double yaw = player.getLocation().getYaw();
        PickupCollision.PlacementResult result = PickupCollision.validatePlacement(this.plugin.pickups().all(), null, at, yaw);
        if (!result.ok()) {
            this.msg(sender, "&c" + result.message());
            return;
        }
        this.plugin.pickups().create(at, yaw);
        this.msg(sender, "&aPickup spawned.");
    }

    private Location findSpawnAnchor(Player player) {
        Block target = player.getTargetBlockExact(12, FluidCollisionMode.NEVER);
        if (target != null && target.getType().isSolid()) {
            return PickupCollision.anchorOnTop(target);
        }
        Location base = player.getLocation().clone();
        Vector fwd = base.getDirection().setY(0);
        if (fwd.lengthSquared() > 0.01) {
            base.add(fwd.normalize().multiply(4.0));
        }
        base.setX(Math.floor(base.getX()) + 0.5);
        base.setZ(Math.floor(base.getZ()) + 0.5);
        return base;
    }

    private void place(CommandSender sender, String[] args) {
        double yaw;
        World world;
        if (!sender.hasPermission("pickupcraft.spawn")) {
            this.msg(sender, "&cYou do not have permission.");
            return;
        }
        if (args.length < 4) {
            this.msg(sender, "&cUsage: /pickup place <x> <y> <z>");
            return;
        }
        Double x = this.parseCoord(args[1]);
        Double y = this.parseCoord(args[2]);
        Double z = this.parseCoord(args[3]);
        if (x == null || y == null || z == null) {
            this.msg(sender, "&cCoordinates must be numbers.");
            return;
        }
        if (sender instanceof Player) {
            Player player = (Player)sender;
            world = player.getWorld();
            yaw = player.getLocation().getYaw();
        } else {
            world = (World)Bukkit.getWorlds().get(0);
            yaw = -90.0;
        }
        Location at = new Location(world, x, y, z);
        PickupCollision.PlacementResult result = PickupCollision.validatePlacement(this.plugin.pickups().all(), null, at, yaw);
        if (!result.ok()) {
            this.msg(sender, "&c" + result.message());
            return;
        }
        this.plugin.pickups().create(at, yaw);
        this.msg(sender, "&aPickup spawned in &f" + world.getName() + " (" + x + ", " + y + ", " + z + ")&a.");
    }

    private Double parseCoord(String s) {
        try {
            return Double.parseDouble(s);
        }
        catch (NumberFormatException ex) {
            return null;
        }
    }

    private void remove(CommandSender sender) {
        Pickup target;
        if (!(sender instanceof Player)) {
            this.msg(sender, "&cPlayers only.");
            return;
        }
        Player player = (Player)sender;
        if (!sender.hasPermission("pickupcraft.admin")) {
            this.msg(sender, "&cPermission pickupcraft.admin is required.");
            return;
        }
        Pickup driving = this.plugin.pickups().byDriver(player.getUniqueId());
        Pickup pickup = target = driving != null ? driving : this.plugin.pickups().byGunner(player.getUniqueId());
        if (target == null) {
            target = this.plugin.pickups().rayTraceFrom(player.getEyeLocation(), 24.0);
        }
        if (target == null) {
            this.msg(sender, "&cLook at the pickup you want to remove.");
            return;
        }
        this.plugin.pickups().remove(target, false);
        this.msg(sender, "&aPickup removed.");
    }

    private void cleanup(CommandSender sender) {
        if (!sender.hasPermission("pickupcraft.admin")) {
            this.msg(sender, "&cPermission pickupcraft.admin is required.");
            return;
        }
        int[] r = this.plugin.pickups().purgeAll();
        this.msg(sender, "&aCleanup complete: removed pickups &f" + r[0] + "&a, removed orphaned entities &f" + r[1] + "&a.");
        if (r[1] > 0) {
            this.msg(sender, "&7(all PickupCraft-tagged entities in loaded chunks were removed)");
        }
    }

    private void migrate(CommandSender sender) {
        if (!sender.hasPermission("pickupcraft.admin")) {
            this.msg(sender, "&cPermission pickupcraft.admin is required.");
            return;
        }
        int[] r = this.plugin.pickups().migrateStale();
        if (r[0] == 0) {
            this.msg(sender, "&aNo stale or unrecognized pickups found; nothing to migrate.");
            return;
        }
        this.msg(sender, "&aMigrated pickups &f" + r[0] + "&a (rebuilt from &f" + r[1] + "&a stale entities). Position and rotation were preserved; health was restored to maximum.");
    }

    private void reload(CommandSender sender) {
        if (!sender.hasPermission("pickupcraft.admin")) {
            this.msg(sender, "&cPermission pickupcraft.admin is required.");
            return;
        }
        this.plugin.bukkitPlugin().reloadAll();
        this.msg(sender, "&aConfiguration reloaded.");
    }

    private void msg(CommandSender sender, String legacy) {
        TextComponent c = LegacyComponentSerializer.legacyAmpersand().deserialize(legacy);
        sender.sendMessage(c);
    }

    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        ArrayList<String> out = new ArrayList<>();
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            for (String s : List.of("give", "spawn", "place", "remove", "cleanup", "list", "reload", "migrate")) {
                if (!s.startsWith(prefix)) continue;
                out.add(s);
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (!p.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) continue;
                out.add(p.getName());
            }
        } else if (args.length >= 2 && args.length <= 4 && args[0].equalsIgnoreCase("place") && sender instanceof Player) {
            Player p = (Player)sender;
            Location loc = p.getLocation();
            double coord = switch (args.length) {
                case 2 -> loc.getX();
                case 3 -> loc.getY();
                default -> loc.getZ();
            };
            out.add(String.valueOf(Math.floor(coord * 100.0) / 100.0));
        }
        return out;
    }

    private static String permissionFor(String sub) {
        return switch (sub) {
            case "give" -> "pickupcraft.give";
            case "spawn", "place" -> "pickupcraft.spawn";
            case "remove", "cleanup", "reload", "migrate" -> "pickupcraft.admin";
            default -> "pickupcraft.use";
        };
    }

    private void executeSub(CommandSender sender, String sub, String[] args) {
        String[] shifted = new String[args.length + 1];
        shifted[0] = sub;
        System.arraycopy(args, 0, shifted, 1, args.length);
        onCommand(sender, null, "mc", shifted);
    }

    private List<String> tabSub(CommandSender sender, String sub, String[] args) {
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
