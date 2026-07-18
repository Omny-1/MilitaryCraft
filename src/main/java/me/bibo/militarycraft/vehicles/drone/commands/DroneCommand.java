package me.bibo.militarycraft.vehicles.drone.commands;

import me.bibo.militarycraft.core.command.SubCommand;
import me.bibo.militarycraft.vehicles.drone.DroneRuntime;
import me.bibo.militarycraft.vehicles.drone.drone.Drone;
import me.bibo.militarycraft.vehicles.drone.items.DroneItem;
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

/** /bpla give|spawn|fire|exit|remove|list|reload|cleanup */
public final class DroneCommand implements CommandExecutor, TabCompleter {

    private static final String USAGE = "/bpla give|spawn|place|fire|exit|remove|list|reload|cleanup";
    private static final List<String> SUBS =
            List.of("give", "spawn", "place", "fire", "exit", "remove", "list", "reload", "cleanup");

    private final DroneRuntime plugin;

    public DroneCommand(DroneRuntime plugin) {
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
            case "fire" -> control(sender, true);
            case "exit" -> control(sender, false);
            case "remove" -> remove(sender);
            case "list" -> sender.sendMessage(Component.text("Active UAVs: "
                    + plugin.drones().count(), NamedTextColor.AQUA));
            case "reload" -> {
                if (notAllowed(sender, "dronecraft.admin")) return true;
                plugin.bukkitPlugin().reloadAll();
                sender.sendMessage(Component.text("DroneCraft: config reloaded.", NamedTextColor.GREEN));
            }
            case "cleanup" -> cleanup(sender);
            default -> sender.sendMessage(Component.text(USAGE, NamedTextColor.AQUA));
        }
        return true;
    }

    /** Manual detonate (fire=true) or exit (fire=false) the UAV you're controlling. */
    private void control(CommandSender sender, boolean fire) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage(Component.text("Players only.", NamedTextColor.RED));
            return;
        }
        Drone drone = plugin.drones().byDriver(p.getUniqueId());
        if (drone == null) {
            p.sendActionBar(Component.text("You are not piloting a UAV", NamedTextColor.RED));
            return;
        }
        if (fire) {
            plugin.drones().detonate(drone, drone.nose());
        } else {
            plugin.drones().exitControl(drone);
        }
    }

    private void give(CommandSender sender, String[] args) {
        if (notAllowed(sender, "dronecraft.use")) {
            return;
        }
        Player target;
        if (args.length >= 2) {
            if (!sender.hasPermission("dronecraft.admin")) {
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
            sender.sendMessage(Component.text("Console usage: /bpla give <player>", NamedTextColor.RED));
            return;
        }
        target.getInventory().addItem(DroneItem.create(plugin));
        target.sendMessage(Component.text("UAV item given. Right-click ground to launch it.",
                NamedTextColor.GREEN));
    }

    private void spawn(CommandSender sender) {
        if (notAllowed(sender, "dronecraft.use")) {
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
        dir.normalize().multiply(3);
        Location at = p.getLocation().add(dir).add(0, 1.2, 0);
        plugin.drones().create(at, p.getLocation().getYaw());
        p.sendMessage(Component.text("UAV launched.", NamedTextColor.GREEN));
    }

    private void place(CommandSender sender, String[] args) {
        if (notAllowed(sender, "dronecraft.use")) {
            return;
        }
        if (args.length < 4) {
            sender.sendMessage(Component.text("Usage: /bpla place <x> <y> <z> [world]", NamedTextColor.RED));
            return;
        }
        Player p = (sender instanceof Player) ? (Player) sender : null;
        Location origin = p != null ? p.getLocation() : null;
        Double x = parseCoord(args[1], origin != null ? origin.getX() : 0, p != null);
        Double y = parseCoord(args[2], origin != null ? origin.getY() : 0, p != null);
        Double z = parseCoord(args[3], origin != null ? origin.getZ() : 0, p != null);
        if (x == null || y == null || z == null) {
            sender.sendMessage(Component.text("Invalid coordinates. Example: /bpla place 100 90 -200", NamedTextColor.RED));
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
        Location at = me.bibo.militarycraft.core.util.CommandCoords.resolve(world, x, y, z);
        if (at == null) {
            sender.sendMessage(Component.text(
                    "Can't place there: invalid coordinates, outside the world border, or an ungenerated chunk. Move closer.",
                    NamedTextColor.RED));
            return;
        }
        at.getChunk().load();
        float yaw = p != null ? p.getLocation().getYaw() : 0f;
        plugin.drones().create(at, yaw);
        sender.sendMessage(Component.text("UAV placed: " + fmt(x, y, z)
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
        if (notAllowed(sender, "dronecraft.admin")) {
            return;
        }
        if (!(sender instanceof Player p)) {
            sender.sendMessage(Component.text("Players only.", NamedTextColor.RED));
            return;
        }
        Drone nearest = null;
        double best = 16 * 16;
        Location loc = p.getLocation();
        for (Drone drone : plugin.drones().all()) {
            if (drone.world() != loc.getWorld()) {
                continue;
            }
            double d = drone.anchor().distanceSquared(loc);
            if (d < best) {
                best = d;
                nearest = drone;
            }
        }
        if (nearest == null) {
            p.sendMessage(Component.text("No UAVs nearby (<=16 blocks).", NamedTextColor.RED));
            return;
        }
        plugin.drones().remove(nearest, false);
        p.sendMessage(Component.text("UAV removed.", NamedTextColor.GREEN));
    }

    private void cleanup(CommandSender sender) {
        if (notAllowed(sender, "dronecraft.admin")) {
            return;
        }
        int[] r = plugin.drones().cleanupAll();
        sender.sendMessage(Component.text("DroneCraft cleaned: UAVs removed " + r[0]
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
            case "remove", "reload", "cleanup" -> "dronecraft.admin";
            default -> "dronecraft.use";
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
