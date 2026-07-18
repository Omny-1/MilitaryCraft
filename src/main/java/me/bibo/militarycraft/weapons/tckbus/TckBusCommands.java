package me.bibo.militarycraft.weapons.tckbus;

import me.bibo.militarycraft.core.command.SubCommand;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.RayTraceResult;

import java.util.ArrayList;
import java.util.List;

/** /tck — give, place, setdrop, cleardrop, showdrop, remove, list, reload, cleanup. */
public final class TckBusCommands implements CommandExecutor, TabCompleter {

    private static final List<String> SUBS = List.of(
            "give", "place", "setdrop", "cleardrop", "showdrop", "remove", "list", "reload", "cleanup");

    private final TckBusRuntime plugin;

    public TckBusCommands(TckBusRuntime plugin) {
        this.plugin = plugin;
    }

    List<SubCommand> all() {
        return SUBS.stream().map(RootSub::new).map(SubCommand.class::cast).toList();
    }

    private static String permissionFor(String sub) {
        return switch (sub) {
            case "place" -> "tckbus.place";
            case "setdrop", "cleardrop", "reload", "cleanup" -> "tckbus.admin";
            default -> "tckbus.use";
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

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            msg(sender, "§e/tck give [player] [tck|tzahal] §7or §e/tck give [tck|tzahal]");
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "give" -> give(sender, args);
            case "place" -> place(sender, args);
            case "setdrop" -> setDrop(sender);
            case "cleardrop" -> clearDrop(sender);
            case "showdrop" -> showDrop(sender);
            case "remove" -> remove(sender);
            case "list" -> msg(sender, "§aActive TCK Buses: §f" + plugin.buses().count());
            case "reload" -> reload(sender);
            case "cleanup" -> cleanup(sender);
            default -> msg(sender, "§cUnknown subcommand: " + args[0]);
        }
        return true;
    }

    private void give(CommandSender sender, String[] args) {
        if (!sender.hasPermission("tckbus.use")) {
            deny(sender);
            return;
        }
        TckBusSettings.Skin skin = plugin.config().defaultSkin();
        Player target = null;
        if (args.length >= 2) {
            if (plugin.config().isSkinName(args[1])) {
                skin = plugin.config().skin(args[1]);
                if (args.length >= 3) {
                    if (!sender.hasPermission("tckbus.admin")) {
                        deny(sender);
                        return;
                    }
                    target = plugin.getServer().getPlayerExact(args[2]);
                    if (target == null) {
                        msg(sender, "§cPlayer not found: " + args[2]);
                        return;
                    }
                }
            } else {
                if (!sender.hasPermission("tckbus.admin")) {
                    deny(sender);
                    return;
                }
                target = plugin.getServer().getPlayerExact(args[1]);
                if (target == null) {
                    msg(sender, "§cPlayer not found: " + args[1]);
                    return;
                }
                if (args.length >= 3) {
                    if (!plugin.config().isSkinName(args[2])) {
                        msg(sender, "§cUnknown skin: " + args[2] + " §7(available: " + String.join(", ", plugin.config().skinSuggestions()) + ")");
                        return;
                    }
                    skin = plugin.config().skin(args[2]);
                }
            }
        }
        if (target == null && sender instanceof Player p) {
            target = p;
        }
        if (target == null) {
            msg(sender, "§cSpecify a player: /tck give <player> [tck|tzahal]");
            return;
        }
        target.getInventory().addItem(TckBusItem.create(skin));
        msg(sender, "§aGave " + skin.displayName + " summons to §f" + target.getName());
    }

    private void place(CommandSender sender, String[] args) {
        // Coordinate mode: /tck place <x> <y> <z> [skin] - works from console too.
        if (args.length >= 4 || (args.length >= 2 && looksLikeCoord(args[1]))) {
            placeAt(sender, args);
            return;
        }
        // Legacy mode: place in front of the player (raytrace / ahead).
        if (!(sender instanceof Player p)) {
            msg(sender, "§cConsole usage: /tck place <x> <y> <z> [skin] [world]");
            return;
        }
        if (!p.hasPermission("tckbus.place")) {
            deny(sender);
            return;
        }
        TckBusSettings.Skin skin = plugin.config().defaultSkin();
        if (args.length >= 2) {
            if (!plugin.config().isSkinName(args[1])) {
                msg(sender, "§cUnknown skin: " + args[1] + " §7(available: " + String.join(", ", plugin.config().skinSuggestions()) + ")");
                return;
            }
            skin = plugin.config().skin(args[1]);
        }
        Location at;
        RayTraceResult hit = p.rayTraceBlocks(6.0);
        if (hit != null && hit.getHitBlock() != null) {
            Block b = hit.getHitBlock();
            at = new Location(b.getWorld(), b.getX() + 0.5, b.getY() + 1.0, b.getZ() + 0.5);
        } else {
            Location f = p.getLocation().clone().add(p.getLocation().getDirection().setY(0).normalize().multiply(2.5));
            at = new Location(p.getWorld(), Math.floor(f.getX()) + 0.5, p.getLocation().getY(), Math.floor(f.getZ()) + 0.5);
        }
        double yaw = plugin.config().yawSnap ? Math.round(p.getLocation().getYaw() / 90.0) * 90.0
                : p.getLocation().getYaw();
        plugin.buses().create(at, yaw, p.getUniqueId(), skin.id);
        msg(sender, "§a" + skin.busName + " placed.");
    }

    /** /tck place <x> <y> <z> [skin] - spawn at explicit coordinates (console-friendly). */
    private void placeAt(CommandSender sender, String[] args) {
        if (!sender.hasPermission("tckbus.place")) {
            deny(sender);
            return;
        }
        if (args.length < 4) {
            msg(sender, "§cUsage: /tck place <x> <y> <z> [skin] [world]");
            return;
        }
        Player p = (sender instanceof Player) ? (Player) sender : null;
        Location origin = p != null ? p.getLocation() : null;
        Double x = parseCoord(args[1], origin != null ? origin.getX() : 0, p != null);
        Double y = parseCoord(args[2], origin != null ? origin.getY() : 0, p != null);
        Double z = parseCoord(args[3], origin != null ? origin.getZ() : 0, p != null);
        if (x == null || y == null || z == null) {
            msg(sender, "§cInvalid coordinates. Example: /tck place 100 70 -200");
            return;
        }
        TckBusSettings.Skin skin = plugin.config().defaultSkin();
        org.bukkit.World world = p != null ? p.getWorld() : plugin.getServer().getWorlds().get(0);
        // args[4] and args[5] are skin and/or world in any order: known skin first, otherwise world.
        for (int i = 4; i < args.length && i <= 5; i++) {
            String token = args[i];
            if (plugin.config().isSkinName(token)) {
                skin = plugin.config().skin(token);
                continue;
            }
            org.bukkit.World w = plugin.getServer().getWorld(token);
            if (w != null) {
                world = w;
                continue;
            }
            msg(sender, "§cUnknown skin or world: " + token
                    + " §7(skins: " + String.join(", ", plugin.config().skinSuggestions()) + ")");
            return;
        }
        Location at = me.bibo.militarycraft.core.util.CommandCoords.safeLocation(world, x, y, z);
        at.getChunk().load();
        double baseYaw = p != null ? p.getLocation().getYaw() : 0.0;
        double yaw = plugin.config().yawSnap ? Math.round(baseYaw / 90.0) * 90.0 : baseYaw;
        plugin.buses().create(at, yaw, p != null ? p.getUniqueId() : null, skin.id);
        msg(sender, "§a" + skin.busName + " placed: §f" + fmt(x, y, z) + " §7(" + world.getName() + ")");
    }

    private static boolean looksLikeCoord(String s) {
        if (s.startsWith("~")) {
            return true;
        }
        try {
            Double.parseDouble(s);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
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

    private void setDrop(CommandSender sender) {
        if (!adminPlayer(sender)) {
            return;
        }
        Player p = (Player) sender;
        // Snapshot the whole hotbar (slots 0-8); each non-empty slot becomes loot.
        List<ItemStack> items = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            ItemStack it = p.getInventory().getItem(i);
            if (it != null && !it.getType().isAir()) {
                items.add(it.clone());
            }
        }
        if (items.isEmpty()) {
            msg(sender, "§cPut items in the hotbar (9 slots) to set the drop.");
            return;
        }
        plugin.drops().set(items);
        msg(sender, "§aBus drop set from §f" + items.size() + " §ahotbar slot(s).");
    }

    private void clearDrop(CommandSender sender) {
        if (!adminPlayer(sender) && !sender.hasPermission("tckbus.admin")) {
            deny(sender);
            return;
        }
        plugin.drops().clear();
        msg(sender, "§aCustom Bus drop cleared.");
    }

    private void showDrop(CommandSender sender) {
        if (!sender.hasPermission("tckbus.use")) {
            deny(sender);
            return;
        }
        List<ItemStack> drops = plugin.drops().get();
        if (drops.isEmpty()) {
            msg(sender, "§7Custom Bus drop is not set. (/tck setdrop)");
            return;
        }
        msg(sender, "§aBus drop (§f" + drops.size() + "§a):");
        for (ItemStack is : drops) {
            msg(sender, "  §7- §f" + is.getAmount() + "x " + is.getType());
        }
    }

    private void remove(CommandSender sender) {
        if (!(sender instanceof Player p)) {
            msg(sender, "§cPlayers only.");
            return;
        }
        if (!p.hasPermission("tckbus.use")) {
            deny(sender);
            return;
        }
        boolean admin = p.hasPermission("tckbus.admin");
        TckBusRig nearest = null;
        double bestSq = 12 * 12;
        for (TckBusRig b : plugin.buses().all()) {
            if (b.world() != p.getWorld()) {
                continue;
            }
            if (!admin && (b.owner() == null || !b.owner().equals(p.getUniqueId()))) {
                continue;
            }
            double dSq = b.anchor().distanceSquared(p.getLocation());
            if (dSq <= bestSq) {
                bestSq = dSq;
                nearest = b;
            }
        }
        if (nearest == null) {
            msg(sender, "§cNo owned TCK Bus nearby.");
            return;
        }
        plugin.buses().remove(nearest, false);
        p.getInventory().addItem(TckBusItem.create(nearest.skin()));
        msg(sender, "§aNearest " + nearest.skin().busName + " removed.");
    }

    private void reload(CommandSender sender) {
        if (!sender.hasPermission("tckbus.admin")) {
            deny(sender);
            return;
        }
        plugin.plugin().reloadAll();
        msg(sender, "§aTCKBus config reloaded.");
    }

    private void cleanup(CommandSender sender) {
        if (!sender.hasPermission("tckbus.admin")) {
            deny(sender);
            return;
        }
        int[] r = plugin.buses().cleanupAll();
        msg(sender, "§aBuses removed: §f" + r[0] + "§a, orphan entities: §f" + r[1]);
    }

    // ----------------------------------------------------------------- helpers

    private boolean adminPlayer(CommandSender sender) {
        if (!(sender instanceof Player)) {
            msg(sender, "§cPlayers only.");
            return false;
        }
        if (!sender.hasPermission("tckbus.admin")) {
            deny(sender);
            return false;
        }
        return true;
    }

    private void deny(CommandSender sender) {
        msg(sender, "§cYou do not have permission.");
    }

    private void msg(CommandSender sender, String legacy) {
        sender.sendMessage(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                .legacySection().deserialize(legacy));
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
            List<String> names = new ArrayList<>(plugin.config().skinSuggestions());
            for (Player p : plugin.getServer().getOnlinePlayers()) {
                names.add(p.getName());
            }
            return filter(names, args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            if (plugin.config().isSkinName(args[1])) {
                List<String> names = new ArrayList<>();
                for (Player p : plugin.getServer().getOnlinePlayers()) {
                    names.add(p.getName());
                }
                return filter(names, args[2]);
            }
            return filter(plugin.config().skinSuggestions(), args[2]);
        }
        if (args[0].equalsIgnoreCase("place")) {
            if (args.length == 2) {
                List<String> opts = new ArrayList<>(plugin.config().skinSuggestions());
                opts.add("~");
                return filter(opts, args[1]);
            }
            if (args.length == 3 || args.length == 4) {
                return List.of("~");
            }
            if (args.length == 5 || args.length == 6) {
                List<String> opts = new ArrayList<>(plugin.config().skinSuggestions());
                for (org.bukkit.World w : plugin.getServer().getWorlds()) {
                    opts.add(w.getName());
                }
                return filter(opts, args[args.length - 1]);
            }
        }
        return List.of();
    }

    private List<String> filter(List<String> source, String prefix) {
        String lower = prefix.toLowerCase();
        List<String> out = new ArrayList<>();
        for (String value : source) {
            if (value.toLowerCase().startsWith(lower)) {
                out.add(value);
            }
        }
        return out;
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


