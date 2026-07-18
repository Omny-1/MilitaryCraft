package me.bibo.militarycraft.vehicles.jet.commands;

import me.bibo.militarycraft.core.command.SubCommand;
import me.bibo.militarycraft.vehicles.jet.JetRuntime;
import me.bibo.militarycraft.vehicles.jet.items.JetItem;
import me.bibo.militarycraft.vehicles.jet.jet.Jet;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

/** /jet give|spawn|remove|list|reload|cleanup */
public final class JetCommand implements CommandExecutor, TabCompleter {

    private static final String USAGE = "/jet give|spawn|place|remove|list|reload|cleanup";
    private static final List<String> SUBS = List.of("give", "spawn", "place", "remove", "list", "reload", "cleanup");

    private final JetRuntime plugin;

    public JetCommand(JetRuntime plugin) {
        this.plugin = plugin;
    }

    public List<SubCommand> all() {
        return SUBS.stream().map(RootSub::new).map(SubCommand.class::cast).toList();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(Component.text(USAGE, NamedTextColor.AQUA));
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "give" -> give(sender, args);
            case "spawn" -> spawn(sender);
            case "place" -> place(sender, args);
            case "remove" -> remove(sender);
            case "list" -> sender.sendMessage(Component.text("Active fighters: "
                    + plugin.jets().count(), NamedTextColor.AQUA));
            case "reload" -> {
                if (notAllowed(sender, "jetcraft.admin")) return true;
                plugin.bukkitPlugin().reloadAll();
                sender.sendMessage(Component.text("JetCraft: config reloaded.", NamedTextColor.GREEN));
            }
            case "cleanup" -> cleanup(sender);
            default -> sender.sendMessage(Component.text(USAGE, NamedTextColor.AQUA));
        }
        return true;
    }

    private void give(CommandSender sender, String[] args) {
        if (notAllowed(sender, "jetcraft.use")) {
            return;
        }
        Player target;
        if (args.length >= 2) {
            if (!sender.hasPermission("jetcraft.admin")) {
                sender.sendMessage(Component.text("You cannot give this to others.", NamedTextColor.RED));
                return;
            }
            target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                sender.sendMessage(Component.text("Player not found: " + args[1], NamedTextColor.RED));
                return;
            }
        } else if (sender instanceof Player p) {
            target = p;
        } else {
            sender.sendMessage(Component.text("Console usage: /jet give <player>", NamedTextColor.RED));
            return;
        }
        target.getInventory().addItem(JetItem.create(plugin));
        target.sendMessage(Component.text("Su-30 Fighter item given. Right-click ground to place it.",
                NamedTextColor.GREEN));
    }

    private void spawn(CommandSender sender) {
        if (notAllowed(sender, "jetcraft.use")) {
            return;
        }
        if (!(sender instanceof Player p)) {
            sender.sendMessage(Component.text("Players only.", NamedTextColor.RED));
            return;
        }
        Location eye = p.getEyeLocation();
        Vector dir = eye.getDirection().setY(0);
        if (dir.lengthSquared() < 1e-6) {
            dir = new Vector(0, 0, 1);
        }
        dir.normalize().multiply(6);
        Location at = p.getLocation().add(dir).add(0, 1.5, 0);
        plugin.jets().create(at, p.getLocation().getYaw());
        p.sendMessage(Component.text("Fighter spawned.", NamedTextColor.GREEN));
    }

    private void place(CommandSender sender, String[] args) {
        if (notAllowed(sender, "jetcraft.use")) {
            return;
        }
        if (args.length < 4) {
            sender.sendMessage(Component.text("Usage: /jet place <x> <y> <z> [world]", NamedTextColor.RED));
            return;
        }
        Player p = (sender instanceof Player) ? (Player) sender : null;
        Location origin = p != null ? p.getLocation() : null;
        Double x = parseCoord(args[1], origin != null ? origin.getX() : 0, p != null);
        Double y = parseCoord(args[2], origin != null ? origin.getY() : 0, p != null);
        Double z = parseCoord(args[3], origin != null ? origin.getZ() : 0, p != null);
        if (x == null || y == null || z == null) {
            sender.sendMessage(Component.text("Invalid coordinates. Example: /jet place 100 90 -200", NamedTextColor.RED));
            return;
        }
        org.bukkit.World world;
        if (args.length >= 5) {
            world = Bukkit.getWorld(args[4]);
            if (world == null) {
                sender.sendMessage(Component.text("World not found: " + args[4], NamedTextColor.RED));
                return;
            }
        } else if (p != null) {
            world = p.getWorld();
        } else {
            world = Bukkit.getWorlds().get(0);
        }
        Location at = me.bibo.militarycraft.core.util.CommandCoords.safeLocation(world, x, y, z);
        at.getChunk().load();
        float yaw = p != null ? p.getLocation().getYaw() : 0f;
        plugin.jets().create(at, yaw);
        sender.sendMessage(Component.text("Fighter placed: " + fmt(x, y, z)
                + " (" + world.getName() + ")", NamedTextColor.GREEN));
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
        if (notAllowed(sender, "jetcraft.admin")) {
            return;
        }
        if (!(sender instanceof Player p)) {
            sender.sendMessage(Component.text("Players only.", NamedTextColor.RED));
            return;
        }
        Jet nearest = null;
        double best = 16 * 16;
        Location loc = p.getLocation();
        for (Jet jet : plugin.jets().all()) {
            if (jet.world() != loc.getWorld()) {
                continue;
            }
            double d = jet.anchor().distanceSquared(loc);
            if (d < best) {
                best = d;
                nearest = jet;
            }
        }
        if (nearest == null) {
            p.sendMessage(Component.text("No fighters nearby (<=16 blocks).", NamedTextColor.RED));
            return;
        }
        plugin.jets().remove(nearest, false);
        p.sendMessage(Component.text("Fighter removed.", NamedTextColor.GREEN));
    }

    private void cleanup(CommandSender sender) {
        if (notAllowed(sender, "jetcraft.admin")) {
            return;
        }
        int[] r = plugin.jets().cleanupAll();
        sender.sendMessage(Component.text("JetCraft cleaned: fighters removed " + r[0]
                + ", orphan entities " + r[1] + ".", NamedTextColor.GREEN));
    }

    private boolean notAllowed(CommandSender sender, String perm) {
        if (!sender.hasPermission(perm)) {
            sender.sendMessage(Component.text("You do not have permission.", NamedTextColor.RED));
            return true;
        }
        return false;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> out = new ArrayList<>();
            for (String s : SUBS) {
                if (s.startsWith(args[0].toLowerCase())) {
                    out.add(s);
                }
            }
            return out;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            List<String> names = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(args[1].toLowerCase())) {
                    names.add(p.getName());
                }
            }
            return names;
        }
        if (args[0].equalsIgnoreCase("place")) {
            if (args.length >= 2 && args.length <= 4) {
                return List.of("~");
            }
            if (args.length == 5) {
                List<String> ws = new ArrayList<>();
                for (org.bukkit.World w : Bukkit.getWorlds()) {
                    ws.add(w.getName());
                }
                return ws;
            }
        }
        return List.of();
    }

    private static String permissionFor(String sub) {
        return switch (sub) {
            case "remove", "reload", "cleanup" -> "jetcraft.admin";
            default -> "jetcraft.use";
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
