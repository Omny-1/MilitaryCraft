package me.bibo.militarycraft.vehicles.kamaz.commands;

import me.bibo.militarycraft.core.command.SubCommand;
import me.bibo.militarycraft.vehicles.kamaz.KamazRuntime;
import me.bibo.militarycraft.vehicles.kamaz.items.TruckItem;
import me.bibo.militarycraft.vehicles.kamaz.truck.Truck;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public final class KamazCommand implements TabExecutor {

    private static final List<String> SUBS = List.of("give", "spawn", "place", "remove", "cleanup", "list", "reload");

    private final KamazRuntime plugin;

    public KamazCommand(KamazRuntime plugin) {
        this.plugin = plugin;
    }

    public List<SubCommand> all() {
        return SUBS.stream().map(RootSub::new).map(SubCommand.class::cast).toList();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            msg(sender, "&a/kamaz give|spawn|place|remove|cleanup|list|reload");
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "give" -> give(sender, args);
            case "spawn" -> spawn(sender);
            case "place" -> place(sender, args);
            case "remove" -> remove(sender);
            case "cleanup", "purge", "clear" -> cleanup(sender);
            case "list" -> msg(sender, "&aActive Kamaz trucks: &f" + plugin.trucks().count());
            case "reload" -> reload(sender);
            default -> msg(sender, "&cUnknown subcommand.");
        }
        return true;
    }

    private void give(CommandSender sender, String[] args) {
        if (!hasAny(sender, "kamazcraft.give", "kamazcraft.admin")) {
            msg(sender, "&cRequires kamazcraft.give.");
            return;
        }
        Player target;
        if (args.length >= 2) {
            target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                msg(sender, "&cPlayer not found.");
                return;
            }
        } else if (sender instanceof Player p) {
            target = p;
        } else {
            msg(sender, "&cSpecify a player: /kamaz give <player>");
            return;
        }
        var leftovers = target.getInventory().addItem(TruckItem.create(plugin));
        leftovers.values().forEach(it -> target.getWorld().dropItemNaturally(target.getLocation(), it));
        msg(sender, "&aGave the Kamaz Pushinka item to &f" + target.getName());
    }

    private void spawn(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            msg(sender, "&cPlayers only.");
            return;
        }
        if (!hasAny(sender, "kamazcraft.spawn", "kamazcraft.admin")) {
            msg(sender, "&cRequires kamazcraft.spawn.");
            return;
        }
        Location at = player.getLocation().clone();
        org.bukkit.util.Vector fwd = at.getDirection().setY(0);
        if (fwd.lengthSquared() > 0.01) {
            at.add(fwd.normalize().multiply(5));
        }
        at.setX(Math.floor(at.getX()) + 0.5);
        at.setZ(Math.floor(at.getZ()) + 0.5);
        double yaw = player.getLocation().getYaw();
        String deny = plugin.trucks().validateCreate(player, at, yaw);
        if (deny != null) {
            msg(sender, "&c" + deny);
            return;
        }
        plugin.trucks().create(at, yaw, player.getUniqueId());
        plugin.trucks().recordCreate(player);
        msg(sender, "&aKamaz \"Pushinka\" created.");
    }

    private void place(CommandSender sender, String[] args) {
        if (!hasAny(sender, "kamazcraft.spawn", "kamazcraft.admin")) {
            msg(sender, "&cRequires kamazcraft.spawn.");
            return;
        }
        if (args.length < 4) {
            msg(sender, "&cUsage: /kamaz place <x> <y> <z> [world]");
            return;
        }
        Player p = (sender instanceof Player) ? (Player) sender : null;
        Location origin = p != null ? p.getLocation() : null;
        Double x = parseCoord(args[1], origin != null ? origin.getX() : 0, p != null);
        Double y = parseCoord(args[2], origin != null ? origin.getY() : 0, p != null);
        Double z = parseCoord(args[3], origin != null ? origin.getZ() : 0, p != null);
        if (x == null || y == null || z == null) {
            msg(sender, "&cInvalid coordinates. Example: /kamaz place 100 70 -200");
            return;
        }
        org.bukkit.World world;
        if (args.length >= 5) {
            world = Bukkit.getWorld(args[4]);
            if (world == null) {
                msg(sender, "&cWorld not found: " + args[4]);
                return;
            }
        } else if (p != null) {
            world = p.getWorld();
        } else {
            world = Bukkit.getWorlds().get(0);
        }
        Location at = me.bibo.militarycraft.core.util.CommandCoords.resolve(world, x, y, z);
        if (at == null) {
            msg(sender, "&cCan't place there: invalid coordinates, outside the world border, or an ungenerated chunk. Move closer.");
            return;
        }
        at.getChunk().load();
        double yaw = p != null ? p.getLocation().getYaw() : 0.0;
        plugin.trucks().create(at, yaw, p != null ? p.getUniqueId() : null);
        msg(sender, "&aKamaz placed: &f" + fmt(x, y, z) + " &7(" + world.getName() + ")");
    }

    /** Absolute number, or ~ / ~offset relative to base (players only). null on parse error. */
    private static Double parseCoord(String token, double base, boolean allowRelative) {
        try {
            if (token.startsWith("~")) {
                if (!allowRelative) return null;
                String rest = token.substring(1);
                return base + (rest.isEmpty() ? 0.0 : Double.parseDouble(rest));
            }
            return Double.parseDouble(token);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String fmt(double x, double y, double z) {
        return String.format(java.util.Locale.ROOT, "%.1f, %.1f, %.1f", x, y, z);
    }

    private void remove(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            msg(sender, "&cPlayers only.");
            return;
        }
        if (!sender.hasPermission("kamazcraft.admin")) {
            msg(sender, "&cRequires kamazcraft.admin.");
            return;
        }
        Truck driving = plugin.trucks().byDriver(player.getUniqueId());
        Truck nearest = driving != null ? driving : nearestTruck(player, 12.0);
        if (nearest == null) {
            msg(sender, "&cNo Kamaz nearby (<=12 blocks).");
            return;
        }
        plugin.trucks().remove(nearest, false);
        msg(sender, "&aKamaz removed.");
    }

    private Truck nearestTruck(Player player, double range) {
        Truck best = null;
        double bestSq = range * range;
        for (Truck truck : plugin.trucks().all()) {
            if (truck.world() != player.getWorld()) {
                continue;
            }
            double sq = truck.anchor().distanceSquared(player.getLocation());
            if (sq <= bestSq) {
                bestSq = sq;
                best = truck;
            }
        }
        return best;
    }

    private void cleanup(CommandSender sender) {
        if (!sender.hasPermission("kamazcraft.admin")) {
            msg(sender, "&cRequires kamazcraft.admin.");
            return;
        }
        int[] r = plugin.trucks().purgeAll();
        msg(sender, "&aCleanup complete: removed Kamaz trucks &f" + r[0]
                + "&a, cleaned orphan entities &f" + r[1] + "&a.");
        if (r[1] > 0) {
            msg(sender, "&7(removed all KamazCraft-tagged entities in loaded chunks)");
        }
    }

    private void reload(CommandSender sender) {
        if (!sender.hasPermission("kamazcraft.admin")) {
            msg(sender, "&cRequires kamazcraft.admin.");
            return;
        }
        plugin.bukkitPlugin().reloadAll();
        msg(sender, "&aConfig reloaded.");
    }

    private void msg(CommandSender sender, String legacy) {
        Component c = net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                .legacyAmpersand().deserialize(legacy);
        sender.sendMessage(c);
    }

    private static boolean hasAny(CommandSender sender, String permission, String fallback) {
        return sender.hasPermission(permission) || sender.hasPermission(fallback);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            for (String s : List.of("give", "spawn", "place", "remove", "cleanup", "list", "reload")) {
                if (s.startsWith(args[0].toLowerCase())) {
                    out.add(s);
                }
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                out.add(p.getName());
            }
        } else if (args[0].equalsIgnoreCase("place")) {
            if (args.length >= 2 && args.length <= 4) {
                out.add("~");
            } else if (args.length == 5) {
                for (org.bukkit.World w : Bukkit.getWorlds()) {
                    out.add(w.getName());
                }
            }
        }
        return out;
    }

    private static String permissionFor(String sub) {
        return switch (sub) {
            case "give" -> "kamazcraft.give";
            case "spawn", "place" -> "kamazcraft.spawn";
            case "remove", "cleanup", "reload" -> "kamazcraft.admin";
            default -> "kamazcraft.use";
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
