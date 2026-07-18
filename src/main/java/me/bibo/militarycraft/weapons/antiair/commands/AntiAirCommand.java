package me.bibo.militarycraft.weapons.antiair.commands;

import me.bibo.militarycraft.weapons.antiair.AntiAirRuntime;
import me.bibo.militarycraft.core.command.SubCommand;
import me.bibo.militarycraft.weapons.antiair.items.TurretItem;
import me.bibo.militarycraft.weapons.antiair.turret.Turret;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

/** /pvo give|place|remove|list|reload|cleanup */
public final class AntiAirCommand implements CommandExecutor, TabCompleter {

    private static final String USAGE = "/pvo give|place|remove|list|reload|cleanup";
    private static final List<String> SUBS = List.of("give", "place", "remove", "list", "reload", "cleanup");

    private final AntiAirRuntime plugin;

    public AntiAirCommand(AntiAirRuntime plugin) {
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
            case "place" -> place(sender);
            case "remove" -> remove(sender);
            case "list" -> sender.sendMessage(Component.text("Active Anti-Air turrets: "
                    + plugin.turrets().count(), NamedTextColor.AQUA));
            case "reload" -> {
                if (notAllowed(sender, "antiaircraft.admin")) return true;
                plugin.bukkitPlugin().reloadAll();
                sender.sendMessage(Component.text("AntiAirCraft: config reloaded.", NamedTextColor.GREEN));
            }
            case "cleanup" -> cleanup(sender);
            default -> sender.sendMessage(Component.text(USAGE, NamedTextColor.AQUA));
        }
        return true;
    }

    private void give(CommandSender sender, String[] args) {
        if (notAllowed(sender, "antiaircraft.use")) {
            return;
        }
        Player target;
        if (args.length >= 2) {
            if (!sender.hasPermission("antiaircraft.admin")) {
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
            sender.sendMessage(Component.text("Console usage: /pvo give <player>", NamedTextColor.RED));
            return;
        }
        target.getInventory().addItem(TurretItem.create(plugin));
        target.sendMessage(Component.text("Anti-Air item given. Right-click ground to place it.",
                NamedTextColor.GREEN));
    }

    private void place(CommandSender sender) {
        if (notAllowed(sender, "antiaircraft.use")) {
            return;
        }
        if (!(sender instanceof Player p)) {
            sender.sendMessage(Component.text("Players only.", NamedTextColor.RED));
            return;
        }
        int maxPer = plugin.config().maxPerPlayer;
        if (maxPer > 0 && !p.hasPermission("antiaircraft.admin")
                && plugin.turrets().countByOwner(p.getUniqueId()) >= maxPer) {
            p.sendMessage(Component.text("Anti-Air limit per player: " + maxPer, NamedTextColor.RED));
            return;
        }
        World world = p.getWorld();
        Vector dir = p.getEyeLocation().getDirection().setY(0);
        if (dir.lengthSquared() < 1e-6) {
            dir = new Vector(0, 0, 1);
        }
        Location spot = p.getLocation().add(dir.normalize().multiply(4));
        int x = spot.getBlockX();
        int z = spot.getBlockZ();
        int y = world.getHighestBlockYAt(x, z) + 1;
        Location at = new Location(world, x + 0.5, y, z + 0.5);
        plugin.turrets().create(at, p.getLocation().getYaw(), p.getUniqueId());
        p.sendMessage(Component.text("Anti-Air placed.", NamedTextColor.GREEN));
    }

    private void remove(CommandSender sender) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage(Component.text("Players only.", NamedTextColor.RED));
            return;
        }
        boolean admin = p.hasPermission("antiaircraft.admin");
        Turret nearest = null;
        double best = 32 * 32;
        Location loc = p.getLocation();
        for (Turret t : plugin.turrets().all()) {
            if (t.world() != loc.getWorld()) {
                continue;
            }
            if (!admin && (t.owner() == null || !t.owner().equals(p.getUniqueId()))) {
                continue;
            }
            double d = t.anchor().distanceSquared(loc);
            if (d < best) {
                best = d;
                nearest = t;
            }
        }
        if (nearest == null) {
            p.sendMessage(Component.text("No owned Anti-Air turrets nearby (<=32 blocks).", NamedTextColor.RED));
            return;
        }
        plugin.turrets().remove(nearest, false);
        p.getInventory().addItem(TurretItem.create(plugin));
        p.sendMessage(Component.text("Anti-Air removed.", NamedTextColor.GREEN));
    }

    private void cleanup(CommandSender sender) {
        if (notAllowed(sender, "antiaircraft.admin")) {
            return;
        }
        int[] r = plugin.turrets().cleanupAll();
        sender.sendMessage(Component.text("AntiAirCraft cleaned: turrets removed " + r[0]
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
        return List.of();
    }

    private static String permissionFor(String sub) {
        return switch (sub) {
            case "reload", "cleanup" -> "antiaircraft.admin";
            default -> "antiaircraft.use";
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
