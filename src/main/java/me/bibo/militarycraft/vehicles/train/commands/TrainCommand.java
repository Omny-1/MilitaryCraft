package me.bibo.militarycraft.vehicles.train.commands;

import me.bibo.militarycraft.vehicles.train.TrainRuntime;
import me.bibo.militarycraft.core.command.SubCommand;
import me.bibo.militarycraft.vehicles.train.items.TrainItem;
import me.bibo.militarycraft.vehicles.train.rail.RailTracer;
import me.bibo.militarycraft.vehicles.train.train.Train;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.BlockCommandSender;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ProxiedCommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class TrainCommand implements TabExecutor {

    private static final List<String> SUBS = List.of("give", "place", "remove", "removeall", "list", "reload");

    private final TrainRuntime plugin;

    public TrainCommand(TrainRuntime plugin) {
        this.plugin = plugin;
    }

    public List<SubCommand> all() {
        return SUBS.stream().map(RootSub::new).map(SubCommand.class::cast).toList();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(Component.text("/train give | place <x> <y> <z> | remove | removeall | list | reload",
                    NamedTextColor.GOLD));
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "give" -> {
                if (!sender.hasPermission("traincraft.give")) {
                    sender.sendMessage(Component.text("You do not have permission.", NamedTextColor.RED));
                    return true;
                }
                Player target = args.length > 1 ? Bukkit.getPlayerExact(args[1])
                        : sender instanceof Player p ? p : null;
                if (target == null) {
                    sender.sendMessage(Component.text("Player not found.", NamedTextColor.RED));
                    return true;
                }
                target.getInventory().addItem(TrainItem.create()).values()
                        .forEach(it -> target.getWorld().dropItemNaturally(target.getLocation(), it));
                sender.sendMessage(Component.text("Gave Desert Express - right-click rails.",
                        NamedTextColor.GREEN));
            }
            case "place" -> handlePlace(sender, args);
            case "remove" -> {
                if (!sender.hasPermission("traincraft.admin")) {
                    sender.sendMessage(Component.text("You do not have permission.", NamedTextColor.RED));
                    return true;
                }
                if (!(sender instanceof Player p)) {
                    sender.sendMessage(Component.text("Players only.", NamedTextColor.RED));
                    return true;
                }
                Train t = plugin.trains().nearest(p.getLocation(), 80);
                if (t == null) {
                    sender.sendMessage(Component.text("No trains nearby (80 blocks).", NamedTextColor.YELLOW));
                    return true;
                }
                plugin.trains().removeTrain(t);
                sender.sendMessage(Component.text("Nearest train removed.", NamedTextColor.GREEN));
            }
            case "removeall" -> {
                if (!sender.hasPermission("traincraft.admin")) {
                    sender.sendMessage(Component.text("You do not have permission.", NamedTextColor.RED));
                    return true;
                }
                int n = plugin.trains().removeAll();
                sender.sendMessage(Component.text("Removed trains: " + n, NamedTextColor.GREEN));
            }
            case "list" -> sender.sendMessage(Component.text(
                    "Running trains: " + plugin.trains().count() + " / " + plugin.cfg().maxTrains,
                    NamedTextColor.GOLD));
            case "reload" -> {
                if (!sender.hasPermission("traincraft.admin")) {
                    sender.sendMessage(Component.text("You do not have permission.", NamedTextColor.RED));
                    return true;
                }
                plugin.bukkitPlugin().reloadAll();
                sender.sendMessage(Component.text("TrainCraft config reloaded.", NamedTextColor.GREEN));
            }
            default -> sender.sendMessage(Component.text("Unknown subcommand. /train", NamedTextColor.RED));
        }
        return true;
    }

    private void handlePlace(CommandSender sender, String[] args) {
        if (!sender.hasPermission("traincraft.admin")) {
            sender.sendMessage(Component.text("You do not have permission.", NamedTextColor.RED));
            return;
        }
        if (args.length != 4 && args.length != 5) {
            sender.sendMessage(Component.text(
                    "Usage: /train place <x> <y> <z> or /train place <world> <x> <y> <z>",
                    NamedTextColor.GOLD));
            return;
        }

        Location base = senderLocation(sender);
        World world;
        int firstCoord;
        if (args.length == 5) {
            world = Bukkit.getWorld(args[1]);
            if (world == null) {
                sender.sendMessage(Component.text("World not found: " + args[1], NamedTextColor.RED));
                return;
            }
            firstCoord = 2;
        } else {
            if (base == null) {
                sender.sendMessage(Component.text(
                        "Console usage requires a world: /train place <world> <x> <y> <z>",
                        NamedTextColor.RED));
                return;
            }
            world = base.getWorld();
            firstCoord = 1;
        }

        if (usesRelativeCoordinate(args, firstCoord) && base == null) {
            sender.sendMessage(Component.text("Relative coordinates work from a player or command block.",
                    NamedTextColor.RED));
            return;
        }

        int x;
        int y;
        int z;
        try {
            x = parseBlockCoordinate(args[firstCoord], base == null ? 0.0 : base.getX());
            y = parseBlockCoordinate(args[firstCoord + 1], base == null ? 0.0 : base.getY());
            z = parseBlockCoordinate(args[firstCoord + 2], base == null ? 0.0 : base.getZ());
        } catch (NumberFormatException ex) {
            sender.sendMessage(Component.text("Coordinates must be numbers or relative values like ~ or ~3.",
                    NamedTextColor.RED));
            return;
        }

        Block rail = railAt(world.getBlockAt(x, y, z));
        if (rail == null) {
            sender.sendMessage(Component.text("No rails at these coordinates: " + x + " " + y + " " + z + ".",
                    NamedTextColor.RED));
            return;
        }

        double yawDegrees = base == null ? 0.0 : base.getYaw();
        Train train = plugin.trains().spawn(rail, sender, yawDegrees);
        if (train != null) {
            sender.sendMessage(Component.text("Train placed at "
                    + rail.getX() + " " + rail.getY() + " " + rail.getZ()
                    + " in world " + rail.getWorld().getName() + ".", NamedTextColor.GREEN));
        }
    }

    private static Location senderLocation(CommandSender sender) {
        if (sender instanceof ProxiedCommandSender proxied) {
            CommandSender callee = proxied.getCallee();
            if (callee != sender) {
                Location loc = senderLocation(callee);
                if (loc != null) {
                    return loc;
                }
            }
            CommandSender caller = proxied.getCaller();
            return caller == sender ? null : senderLocation(caller);
        }
        if (sender instanceof Entity entity) {
            return entity.getLocation();
        }
        if (sender instanceof BlockCommandSender blockSender) {
            return blockSender.getBlock().getLocation().add(0.5, 0.5, 0.5);
        }
        return null;
    }

    private static boolean usesRelativeCoordinate(String[] args, int firstCoord) {
        return args[firstCoord].startsWith("~")
                || args[firstCoord + 1].startsWith("~")
                || args[firstCoord + 2].startsWith("~");
    }

    private static int parseBlockCoordinate(String raw, double base) {
        if (raw.startsWith("~")) {
            String offset = raw.substring(1);
            double relative = offset.isEmpty() ? 0.0 : Double.parseDouble(offset);
            return (int) Math.floor(base + relative);
        }
        return (int) Math.floor(Double.parseDouble(raw));
    }

    private static Block railAt(Block block) {
        if (RailTracer.isRail(block)) {
            return block;
        }
        Block below = block.getRelative(0, -1, 0);
        return RailTracer.isRail(below) ? below : null;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(SUBS, args[0]);
        }
        if (args.length > 1 && args[0].equalsIgnoreCase("place")) {
            return completePlace(sender, args);
        }
        return List.of();
    }

    private static List<String> completePlace(CommandSender sender, String[] args) {
        Location loc = senderLocation(sender);
        if (args.length == 2) {
            List<String> suggestions = new ArrayList<>();
            if (loc != null) {
                suggestions.add(Integer.toString(loc.getBlockX()));
                suggestions.add("~");
            }
            Bukkit.getWorlds().stream().map(World::getName).forEach(suggestions::add);
            return filter(suggestions, args[1]);
        }

        int firstCoord = Bukkit.getWorld(args[1]) == null ? 1 : 2;
        int coordNumber = args.length - firstCoord;
        if (coordNumber < 1 || coordNumber > 3 || loc == null) {
            return List.of();
        }

        String value = switch (coordNumber) {
            case 1 -> Integer.toString(loc.getBlockX());
            case 2 -> Integer.toString(loc.getBlockY());
            default -> Integer.toString(loc.getBlockZ());
        };
        return filter(List.of(value, "~"), args[args.length - 1]);
    }

    private static List<String> filter(List<String> options, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        return options.stream()
                .filter(option -> option.toLowerCase(Locale.ROOT).startsWith(lower))
                .toList();
    }

    private static String permissionFor(String sub) {
        return switch (sub) {
            case "give" -> "traincraft.give";
            case "place", "remove", "removeall", "reload" -> "traincraft.admin";
            default -> "traincraft.use";
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
