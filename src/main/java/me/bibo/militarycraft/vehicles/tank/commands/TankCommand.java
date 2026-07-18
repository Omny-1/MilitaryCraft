package me.bibo.militarycraft.vehicles.tank.commands;

import me.bibo.militarycraft.core.command.SubCommand;
import me.bibo.militarycraft.vehicles.tank.TankRuntime;
import me.bibo.militarycraft.vehicles.tank.items.TankItem;
import me.bibo.militarycraft.vehicles.tank.tank.Tank;
import me.bibo.militarycraft.vehicles.tank.tank.TankCollision;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class TankCommand implements TabExecutor {

    private static final List<String> SUBS = List.of("give", "spawn", "place", "remove", "cleanup", "list", "reload");

    private final TankRuntime plugin;

    public TankCommand(TankRuntime plugin) {
        this.plugin = plugin;
    }

    public List<SubCommand> all() {
        return SUBS.stream().map(RootSub::new).map(SubCommand.class::cast).toList();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            msg(sender, "&a/tank give|spawn|place|remove|cleanup|list|reload");
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "give" -> give(sender, args);
            case "spawn" -> spawn(sender);
            case "place" -> place(sender, args);
            case "remove" -> remove(sender);
            case "cleanup", "purge", "clear" -> cleanup(sender);
            case "list" -> msg(sender, "&aActive tanks: &f" + plugin.tanks().count());
            case "reload" -> reload(sender);
            default -> msg(sender, "&cUnknown subcommand.");
        }
        return true;
    }

    private void give(CommandSender sender, String[] args) {
        if (!sender.hasPermission("tankcraft.give")) {
            msg(sender, "&cYou do not have permission.");
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
            msg(sender, "&cSpecify a player: /tank give <player>");
            return;
        }
        Map<Integer, ItemStack> leftovers = target.getInventory().addItem(TankItem.create(plugin));
        if (!leftovers.isEmpty()) {
            leftovers.values().forEach(item -> target.getWorld().dropItemNaturally(target.getLocation(), item));
            msg(sender, "&ePlayer inventory is full: the Tank item was dropped near &f" + target.getName());
            return;
        }
        msg(sender, "&aGave the Tank item to &f" + target.getName());
    }

    private void spawn(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            msg(sender, "&cPlayers only.");
            return;
        }
        if (!sender.hasPermission("tankcraft.spawn")) {
            msg(sender, "&cYou do not have permission.");
            return;
        }
        Location at = findSpawnAnchor(player);
        double yaw = player.getLocation().getYaw();
        plugin.tanks().create(at, yaw);
        msg(sender, "&aTank created.");
    }

    private Location findSpawnAnchor(Player player) {
        Block target = player.getTargetBlockExact(12, FluidCollisionMode.NEVER);
        if (target != null && target.getType().isSolid()) {
            return TankCollision.anchorOnTop(target);
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
        if (!sender.hasPermission("tankcraft.spawn") && !sender.hasPermission("tankcraft.admin")) {
            msg(sender, "&cYou do not have permission.");
            return;
        }
        if (args.length < 4) {
            msg(sender, "&cUsage: /tank place <x> <y> <z> [world]");
            return;
        }
        Player p = (sender instanceof Player) ? (Player) sender : null;
        Location origin = p != null ? p.getLocation() : null;
        Double x = parseCoord(args[1], origin != null ? origin.getX() : 0, p != null);
        Double y = parseCoord(args[2], origin != null ? origin.getY() : 0, p != null);
        Double z = parseCoord(args[3], origin != null ? origin.getZ() : 0, p != null);
        if (x == null || y == null || z == null) {
            msg(sender, "&cInvalid coordinates. Example: /tank place 100 70 -200");
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
        plugin.tanks().create(at, yaw);
        msg(sender, "&aTank placed: &f" + fmt(x, y, z) + " &7(" + world.getName() + ")");
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
        if (!sender.hasPermission("tankcraft.admin")) {
            msg(sender, "&cRequires tankcraft.admin.");
            return;
        }
        Tank driving = plugin.tanks().byDriver(player.getUniqueId());
        Tank target = driving != null ? driving : plugin.tanks().rayTraceFrom(player.getEyeLocation(), 24.0);
        if (target == null) {
            msg(sender, "&cLook at the tank you want to remove.");
            return;
        }
        plugin.tanks().remove(target, false);
        msg(sender, "&aTank removed.");
    }

    private void cleanup(CommandSender sender) {
        if (!sender.hasPermission("tankcraft.admin")) {
            msg(sender, "&cRequires tankcraft.admin.");
            return;
        }
        int[] r = plugin.tanks().purgeAll();
        msg(sender, "&aCleanup complete: removed tanks &f" + r[0]
                + "&a, cleaned orphan entities &f" + r[1] + "&a.");
        if (r[1] > 0) {
            msg(sender, "&7(removed all TankCraft-tagged entities in loaded chunks)");
        }
    }

    private void reload(CommandSender sender) {
        if (!sender.hasPermission("tankcraft.admin")) {
            msg(sender, "&cRequires tankcraft.admin.");
            return;
        }
        plugin.bukkitPlugin().reloadAll();
        msg(sender, "&aConfig reloaded.");
    }

    private void msg(CommandSender sender, String legacy) {
        // tiny legacy-ish formatter for & colour codes
        Component c = net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                .legacyAmpersand().deserialize(legacy);
        sender.sendMessage(c);
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
            case "give" -> "tankcraft.give";
            case "spawn", "place" -> "tankcraft.spawn";
            case "remove", "cleanup", "reload" -> "tankcraft.admin";
            default -> "tankcraft.use";
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
