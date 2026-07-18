package me.bibo.militarycraft.weapons.nuke;

import me.bibo.militarycraft.core.command.SubCommand;
import me.bibo.militarycraft.core.text.Text;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

public final class NukeCommands implements CommandExecutor, TabCompleter {

    private final NukeManager manager;

    NukeCommands(NukeManager manager) {
        this.manager = manager;
    }

    List<SubCommand> all() {
        return List.of(
                sub("strike", "use", this::strike, null),
                sub("place", "use", this::place, this::tabPlace),
                sub("item", "give", this::item, null),
                sub("give", "give", this::item, null),
                sub("reload", "reload", this::reload, null));
    }

    private SubCommand sub(String name, String action, BiConsumer<CommandSender, String[]> exec,
                           BiFunction<CommandSender, String[], List<String>> tab) {
        return new Sub(name, "nuke." + action, exec, tab);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("place")) {
            handlePlace(sender, args);
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(manager.message("players-only"));
            return true;
        }
        if (!player.hasPermission("nuke.use")) {
            player.sendMessage(manager.message("no-permission"));
            return true;
        }
        if (args.length == 0) {
            int maxDist = manager.settings().getInt("max-target-distance", 160);
            Location target = NukeTargeting.resolveTarget(player, maxDist);
            manager.callNuke(player, target);
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "item", "give" -> item(sender, new String[0]);
            case "reload" -> reload(sender, new String[0]);
            default -> sendHelp(player);
        }
        return true;
    }

    private void strike(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(manager.message("players-only"));
            return;
        }
        int maxDist = manager.settings().getInt("max-target-distance", 160);
        manager.callNuke(player, NukeTargeting.resolveTarget(player, maxDist));
    }

    private void place(CommandSender sender, String[] args) {
        String[] shifted = new String[args.length + 1];
        shifted[0] = "place";
        System.arraycopy(args, 0, shifted, 1, args.length);
        handlePlace(sender, shifted);
    }

    private void item(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(manager.message("players-only"));
            return;
        }
        if (!player.hasPermission("nuke.give")) {
            player.sendMessage(manager.message("no-permission"));
            return;
        }
        player.getInventory().addItem(NukeItem.create(manager.core().plugin()));
        player.sendMessage(manager.message("item-given"));
    }

    private void reload(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(manager.message("players-only"));
            return;
        }
        if (!player.hasPermission("nuke.reload")) {
            player.sendMessage(manager.message("no-permission"));
            return;
        }
        manager.core().plugin().reloadAll();
        player.sendMessage(manager.message("reloaded"));
    }

    private void handlePlace(CommandSender sender, String[] args) {
        if (!sender.hasPermission("nuke.use")) {
            sender.sendMessage(manager.message("no-permission"));
            return;
        }
        if (args.length < 4) {
            sender.sendMessage(manager.message("bad-coords"));
            return;
        }
        double x;
        double y;
        double z;
        try {
            x = Double.parseDouble(args[1]);
            y = Double.parseDouble(args[2]);
            z = Double.parseDouble(args[3]);
        } catch (NumberFormatException e) {
            sender.sendMessage(manager.message("bad-coords"));
            return;
        }

        World world;
        if (args.length >= 5) {
            world = Bukkit.getWorld(args[4]);
            if (world == null) {
                sender.sendMessage(manager.message("unknown-world", "world", args[4]));
                return;
            }
        } else if (sender instanceof Player player) {
            world = player.getWorld();
        } else {
            List<World> worlds = Bukkit.getWorlds();
            if (worlds.isEmpty()) {
                sender.sendMessage(manager.message("unknown-world", "world", "?"));
                return;
            }
            world = worlds.get(0);
        }

        Location target = new Location(world, Math.floor(x) + 0.5, y, Math.floor(z) + 0.5);
        Player caller = sender instanceof Player player ? player : null;
        boolean started = manager.callNuke(caller, target);
        if (started) {
            sender.sendMessage(manager.message("place-called",
                    "x", target.getBlockX(), "y", target.getBlockY(), "z", target.getBlockZ(),
                    "world", world.getName()));
        }
    }

    private void sendHelp(Player player) {
        player.sendMessage(Text.of("&2&l☢ NukeStrike &7- commands:"));
        player.sendMessage(Text.of("&a/nuke &7- nuclear strike at your crosshair target"));
        player.sendMessage(Text.of("&a/nuke place <x> <y> <z> [world] &7- strike exact coordinates"));
        if (player.hasPermission("nuke.give")) {
            player.sendMessage(Text.of("&a/nuke item &7- get the nuclear briefcase"));
        }
        if (player.hasPermission("nuke.reload")) {
            player.sendMessage(Text.of("&a/nuke reload &7- reload config"));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> options = new ArrayList<>();
            options.add("place");
            if (sender.hasPermission("nuke.give")) {
                options.add("item");
                options.add("give");
            }
            if (sender.hasPermission("nuke.reload")) {
                options.add("reload");
            }
            return StringUtil.copyPartialMatches(args[0], options, new ArrayList<>());
        }
        if (args.length == 5 && args[0].equalsIgnoreCase("place")) {
            List<String> worlds = new ArrayList<>();
            for (World world : Bukkit.getWorlds()) {
                worlds.add(world.getName());
            }
            return StringUtil.copyPartialMatches(args[4], worlds, new ArrayList<>());
        }
        return Collections.emptyList();
    }

    private List<String> tabPlace(CommandSender sender, String[] args) {
        if (args.length == 4) {
            return Bukkit.getWorlds().stream().map(World::getName).toList();
        }
        return List.of();
    }

    private record Sub(String name, String permission,
                       BiConsumer<CommandSender, String[]> exec,
                       BiFunction<CommandSender, String[], List<String>> tab) implements SubCommand {
        @Override
        public void execute(CommandSender sender, String[] args) {
            exec.accept(sender, args);
        }

        @Override
        public List<String> tabComplete(CommandSender sender, String[] args) {
            return tab == null ? List.of() : tab.apply(sender, args);
        }
    }
}
