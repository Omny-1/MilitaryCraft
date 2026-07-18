package me.bibo.militarycraft.vehicles.moto.commands;

import me.bibo.militarycraft.core.command.SubCommand;
import me.bibo.militarycraft.vehicles.moto.MotoRuntime;
import me.bibo.militarycraft.vehicles.moto.items.MotorcycleItem;
import me.bibo.militarycraft.vehicles.moto.motorcycle.Motorcycle;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Administrative and player-facing command surface for {@code /moto}. */
public final class MotoCommand implements TabExecutor {

    private static final LegacyComponentSerializer LEGACY =
            LegacyComponentSerializer.legacyAmpersand();
    private static final List<String> ROOT_SUBS =
            List.of("give", "spawn", "place", "remove", "cleanup", "list", "reload");

    private final MotoRuntime plugin;

    public MotoCommand(MotoRuntime plugin) {
        this.plugin = plugin;
    }

    public List<SubCommand> all() {
        return ROOT_SUBS.stream().map(RootSub::new).map(SubCommand.class::cast).toList();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            help(sender);
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "give" -> give(sender, args);
            case "spawn" -> spawn(sender, args);
            case "place" -> place(sender, args);
            case "remove" -> remove(sender);
            case "cleanup", "purge", "clear" -> cleanup(sender);
            case "list" -> msg(sender, "&aLoaded motorcycles: &f" + plugin.motorcycles().count());
            case "reload" -> reload(sender);
            default -> {
                msg(sender, "&cUnknown subcommand.");
                help(sender);
            }
        }
        return true;
    }

    private void help(CommandSender sender) {
        msg(sender, "&c/moto give [player] [solo] &7- give the item (solo = no sidecar)");
        msg(sender, "&c/moto spawn [solo] &7- create one in front of you");
        msg(sender, "&c/moto place <x> <y> <z> [world] [yaw] [solo] &7- exact placement");
        msg(sender, "&c/moto remove|cleanup|list|reload");
    }

    private void give(CommandSender sender, String[] args) {
        if (!hasAny(sender, "motocraft.give", "motocraft.admin")) {
            msg(sender, "&cRequires motocraft.give.");
            return;
        }
        boolean withSidecar = !hasSoloFlag(args);
        String targetName = firstNonFlag(args);
        Player target;
        if (targetName != null) {
            target = Bukkit.getPlayerExact(targetName);
            if (target == null) {
                msg(sender, "&cPlayer not found.");
                return;
            }
        } else if (sender instanceof Player player) {
            target = player;
        } else {
            msg(sender, "&cConsole usage: /moto give <player> [solo]");
            return;
        }
        target.getInventory().addItem(MotorcycleItem.create(plugin, withSidecar))
                .forEach((slot, item) ->
                        target.getWorld().dropItemNaturally(target.getLocation(), item));
        msg(sender, "&aMotorcycle item (" + variantName(withSidecar)
                + ") given to &f" + target.getName() + "&a.");
    }

    private void spawn(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            msg(sender, "&cPlayers only.");
            return;
        }
        if (!hasAny(sender, "motocraft.spawn", "motocraft.admin")) {
            msg(sender, "&cRequires motocraft.spawn.");
            return;
        }
        Location at = player.getLocation().clone();
        Vector forward = at.getDirection().setY(0);
        if (forward.lengthSquared() > 1.0e-6) {
            at.add(forward.normalize().multiply(3.5));
        }
        at.setX(Math.floor(at.getX()) + 0.5);
        at.setZ(Math.floor(at.getZ()) + 0.5);
        createValidated(sender, player, at, player.getLocation().getYaw(), !hasSoloFlag(args));
    }

    private void place(CommandSender sender, String[] args) {
        if (!hasAny(sender, "motocraft.spawn", "motocraft.admin")) {
            msg(sender, "&cRequires motocraft.spawn.");
            return;
        }
        boolean withSidecar = !hasSoloFlag(args);
        String[] pos = withoutSoloTokens(args); // coordinates without any solo token
        if (pos.length < 4) {
            msg(sender, "&cUsage: /moto place <x> <y> <z> [world] [yaw] [solo]");
            return;
        }
        Player player = sender instanceof Player p ? p : null;
        Location origin = player == null ? null : player.getLocation();
        Double x = parseCoordinate(pos[1], origin == null ? 0.0 : origin.getX(), player != null);
        Double y = parseCoordinate(pos[2], origin == null ? 0.0 : origin.getY(), player != null);
        Double z = parseCoordinate(pos[3], origin == null ? 0.0 : origin.getZ(), player != null);
        if (x == null || y == null || z == null) {
            msg(sender, "&cInvalid or infinite coordinates.");
            return;
        }

        World world;
        if (pos.length >= 5) {
            world = Bukkit.getWorld(pos[4]);
            if (world == null) {
                msg(sender, "&cWorld not found: " + pos[4]);
                return;
            }
        } else if (player != null) {
            world = player.getWorld();
        } else {
            msg(sender, "&cConsole usage requires a world.");
            return;
        }

        double yaw = player == null ? 0.0 : player.getLocation().getYaw();
        if (pos.length >= 6) {
            Double parsedYaw = parseFinite(pos[5]);
            if (parsedYaw == null) {
                msg(sender, "&cYaw must be a finite number.");
                return;
            }
            yaw = parsedYaw;
        }
        Location at = me.bibo.militarycraft.core.util.CommandCoords.resolve(world, x, y, z);
        if (at == null) {
            msg(sender, "&cCan't place there: invalid coordinates, outside the world border, or an ungenerated chunk. Move closer.");
            return;
        }
        if (createValidated(sender, player, at, yaw, withSidecar)) {
            msg(sender, "&7Coordinates: " + format(x, y, z) + " (" + world.getName() + ").");
        }
    }

    private boolean createValidated(CommandSender sender, Player owner, Location at, double yaw,
                                    boolean withSidecar) {
        String denial = plugin.motorcycles().validateCreate(owner, at, yaw, withSidecar);
        if (denial != null) {
            msg(sender, "&c" + denial);
            return false;
        }
        try {
            plugin.motorcycles().create(at, yaw, owner == null ? null : owner.getUniqueId(),
                    withSidecar);
            plugin.motorcycles().recordCreate(owner);
            msg(sender, "&aMotorcycle (" + variantName(withSidecar) + ") created.");
            return true;
        } catch (RuntimeException ex) {
            plugin.getLogger().severe("Could not create motorcycle: " + ex);
            msg(sender, "&cCould not create motorcycle; details were written to console.");
            return false;
        }
    }

    private static final java.util.Set<String> SOLO_TOKENS = java.util.Set.of(
            "solo", "odinochka", "bez", "bezkolyaski", "nosidecar", "single");

    private static boolean isSoloToken(String value) {
        return value != null && SOLO_TOKENS.contains(value.toLowerCase(Locale.ROOT));
    }

    private static boolean hasSoloFlag(String[] args) {
        for (int i = 1; i < args.length; i++) {
            if (isSoloToken(args[i])) {
                return true;
            }
        }
        return false;
    }

    private static String firstNonFlag(String[] args) {
        for (int i = 1; i < args.length; i++) {
            if (!isSoloToken(args[i])) {
                return args[i];
            }
        }
        return null;
    }

    private static String[] withoutSoloTokens(String[] args) {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < args.length; i++) {
            if (i == 0 || !isSoloToken(args[i])) {
                out.add(args[i]);
            }
        }
        return out.toArray(new String[0]);
    }

    private static String variantName(boolean withSidecar) {
        return withSidecar ? "with sidecar" : "solo";
    }

    private void remove(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            msg(sender, "&cPlayers only.");
            return;
        }
        if (!sender.hasPermission("motocraft.admin")) {
            msg(sender, "&cRequires motocraft.admin.");
            return;
        }
        Motorcycle aboard = plugin.motorcycles().byDriver(player.getUniqueId());
        if (aboard == null) {
            aboard = plugin.motorcycles().byPassenger(player.getUniqueId());
        }
        Motorcycle selected = aboard == null ? nearestMotorcycle(player, 10.0) : aboard;
        if (selected == null) {
            msg(sender, "&cNo loaded motorcycle within 10 blocks.");
            return;
        }
        plugin.motorcycles().remove(selected, false);
        msg(sender, "&aMotorcycle removed.");
    }

    private Motorcycle nearestMotorcycle(Player player, double range) {
        Motorcycle best = null;
        double bestSquared = range * range;
        for (Motorcycle motorcycle : plugin.motorcycles().all()) {
            if (motorcycle.world() != player.getWorld()) {
                continue;
            }
            double squared = motorcycle.anchor().distanceSquared(player.getLocation());
            if (squared <= bestSquared) {
                bestSquared = squared;
                best = motorcycle;
            }
        }
        return best;
    }

    private void cleanup(CommandSender sender) {
        if (!sender.hasPermission("motocraft.admin")) {
            msg(sender, "&cRequires motocraft.admin.");
            return;
        }
        int[] result = plugin.motorcycles().purgeAll();
        msg(sender, "&aRemoved loaded motorcycles: &f" + result[0]
                + "&a; extra orphan entities: &f" + result[1] + "&a.");
        msg(sender, "&7Entities in unloaded chunks can be cleaned after their chunks load.");
    }

    private void reload(CommandSender sender) {
        if (!sender.hasPermission("motocraft.admin")) {
            msg(sender, "&cRequires motocraft.admin.");
            return;
        }
        plugin.bukkitPlugin().reloadAll();
        msg(sender, "&aConfiguration reloaded; model and temporary seats updated.");
    }

    private static Double parseCoordinate(String token, double base, boolean relativeAllowed) {
        try {
            double value;
            if (token.startsWith("~")) {
                if (!relativeAllowed) {
                    return null;
                }
                String suffix = token.substring(1);
                value = base + (suffix.isEmpty() ? 0.0 : Double.parseDouble(suffix));
            } else {
                value = Double.parseDouble(token);
            }
            return Double.isFinite(value) ? value : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static Double parseFinite(String token) {
        try {
            double value = Double.parseDouble(token);
            return Double.isFinite(value) ? value : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static String format(double x, double y, double z) {
        return String.format(Locale.ROOT, "%.1f, %.1f, %.1f", x, y, z);
    }

    private static void msg(CommandSender sender, String legacy) {
        Component component = LEGACY.deserialize(legacy);
        sender.sendMessage(component);
    }

    private static boolean hasAny(CommandSender sender, String permission, String fallback) {
        return sender.hasPermission(permission) || sender.hasPermission(fallback);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> result = new ArrayList<>();
        if (args.length == 1) {
            for (String value : allowedSubcommands(sender)) {
                if (value.startsWith(args[0].toLowerCase(Locale.ROOT))) {
                    result.add(value);
                }
            }
        } else if (args[0].equalsIgnoreCase("give")) {
            if (args.length == 2) {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (player.getName().toLowerCase(Locale.ROOT)
                            .startsWith(args[1].toLowerCase(Locale.ROOT))) {
                        result.add(player.getName());
                    }
                }
                result.add("solo");
            } else if (args.length == 3) {
                result.add("solo");
            }
        } else if (args[0].equalsIgnoreCase("spawn") && args.length == 2) {
            result.add("solo");
        } else if (args[0].equalsIgnoreCase("place")) {
            if (args.length >= 2 && args.length <= 4 && sender instanceof Player) {
                result.add("~");
            } else if (args.length == 5) {
                Bukkit.getWorlds().forEach(world -> result.add(world.getName()));
            } else if (args.length == 6) {
                result.add("0");
                result.add("90");
                result.add("180");
                result.add("-90");
            } else if (args.length == 7) {
                result.add("solo");
            }
        }
        return result;
    }

    private static List<String> allowedSubcommands(CommandSender sender) {
        List<String> values = new ArrayList<>();
        if (hasAny(sender, "motocraft.give", "motocraft.admin")) {
            values.add("give");
        }
        if (hasAny(sender, "motocraft.spawn", "motocraft.admin")) {
            values.add("spawn");
            values.add("place");
        }
        values.add("list");
        if (sender.hasPermission("motocraft.admin")) {
            values.add("remove");
            values.add("cleanup");
            values.add("reload");
        }
        return values;
    }

    private static String permissionFor(String sub) {
        return switch (sub) {
            case "give" -> "motocraft.give";
            case "spawn", "place" -> "motocraft.spawn";
            case "remove", "cleanup", "reload" -> "motocraft.admin";
            default -> "motocraft.use";
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
