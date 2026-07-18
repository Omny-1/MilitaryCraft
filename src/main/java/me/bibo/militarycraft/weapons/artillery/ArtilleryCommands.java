package me.bibo.militarycraft.weapons.artillery;

import me.bibo.militarycraft.core.command.CommandArgs;
import me.bibo.militarycraft.core.command.SubCommand;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

/** The complete `/mc artillery` command group. */
final class ArtilleryCommands implements TabExecutor {

    private final ArtilleryManager manager;

    ArtilleryCommands(ArtilleryManager manager) {
        this.manager = manager;
    }

    List<SubCommand> all() {
        return List.of(
                sub("give", "give", this::give, this::tabPlayers),
                sub("create", "create", this::create, null),
                sub("spawn", "spawn", this::spawn, null),
                sub("place", "spawn", this::place, this::tabPlace),
                sub("enter", "enter", this::enter, null),
                sub("fire", "fire", this::fire, null),
                sub("exit", "exit", this::exit, null),
                sub("rotate", "rotate", this::rotate, null),
                sub("remove", "remove", this::remove, null),
                sub("list", "list", this::list, null),
                sub("cleanup", "cleanup", this::cleanup, null));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String name = command == null ? label : command.getName();
        if ("shoot".equalsIgnoreCase(name) || "arta".equalsIgnoreCase(label)) {
            return shoot(sender, args);
        }
        return artillery(sender, args);
    }

    private boolean shoot(CommandSender sender, String[] args) {
        if (args.length == 1 && args[0].equalsIgnoreCase("exit")) {
            Player player = requirePlayer(sender);
            if (player == null) {
                return true;
            }
            if (!canUseDirect(sender, "exit")) {
                deny(sender);
                return true;
            }
            if (manager.sessions().close(player)) {
                ArtilleryMessages.send(sender, "&aArtillery session closed.");
            } else {
                ArtilleryMessages.send(sender, "&cNo active artillery session found.");
            }
            return true;
        }
        if (args.length != 2) {
            ArtilleryMessages.send(sender, "&cUsage: /shoot <x> <z>");
            return true;
        }
        Player player = requirePlayer(sender);
        if (player == null) {
            return true;
        }
        if (!canUseDirect(sender, "fire")) {
            deny(sender);
            return true;
        }
        Double x = ArtilleryTargetValidator.parseFinite(args[0]);
        Double z = ArtilleryTargetValidator.parseFinite(args[1]);
        if (x == null || z == null) {
            ArtilleryMessages.send(sender, "&cX and Z must be finite real numbers.");
            return true;
        }
        ArtilleryManager.FireResult result = manager.fire(player, x, z);
        ArtilleryMessages.send(sender, result.message());
        return true;
    }

    private boolean artillery(CommandSender sender, String[] args) {
        if (args.length == 0) {
            ArtilleryMessages.send(sender, "&a/artillery <give|create|spawn|place|enter|fire|exit|rotate|remove|list|cleanup|reload>");
            return true;
        }
        String subName = args[0].toLowerCase(Locale.ROOT);
        if ("reload".equals(subName)) {
            if (!sender.hasPermission("militarycraft.admin") && !sender.hasPermission("svoart.admin")) {
                deny(sender);
                return true;
            }
            manager.core().plugin().reloadAll();
            ArtilleryMessages.send(sender, "&aConfiguration and artillery module reloaded.");
            return true;
        }
        if ("setmaproom".equals(subName) || "setmap".equals(subName)) {
            if (!sender.hasPermission("militarycraft.admin") && !sender.hasPermission("svoart.admin")) {
                deny(sender);
                return true;
            }
            ArtilleryMessages.send(sender, "&eMap-room targeting is no longer used. Right-click artillery for a top-down camera, then use /shoot <x> <z>.");
            return true;
        }
        for (SubCommand sub : all()) {
            if (!sub.name().equalsIgnoreCase(subName)) {
                continue;
            }
            if (!canUseDirect(sender, sub)) {
                deny(sender);
                return true;
            }
            sub.execute(sender, Arrays.copyOfRange(args, 1, args.length));
            return true;
        }
        ArtilleryMessages.send(sender, "&cUnknown artillery command: &f" + args[0]);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        String name = command == null ? label : command.getName();
        if ("shoot".equalsIgnoreCase(name) || "arta".equalsIgnoreCase(label)) {
            if (args.length == 1) {
                return List.of("exit");
            }
            return List.of();
        }
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            List<String> out = new ArrayList<>();
            for (SubCommand sub : all()) {
                if (canUseDirect(sender, sub) && sub.name().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                    out.add(sub.name());
                }
            }
            if (canUseDirect(sender, "reload") && "reload".startsWith(prefix)) {
                out.add("reload");
            }
            if ((sender.hasPermission("militarycraft.admin") || sender.hasPermission("svoart.admin"))
                    && "setmaproom".startsWith(prefix)) {
                out.add("setmaproom");
            }
            if ((sender.hasPermission("militarycraft.admin") || sender.hasPermission("svoart.admin"))
                    && "setmap".startsWith(prefix)) {
                out.add("setmap");
            }
            return out;
        }
        for (SubCommand sub : all()) {
            if (sub.name().equalsIgnoreCase(args[0]) && canUseDirect(sender, sub)) {
                return sub.tabComplete(sender, Arrays.copyOfRange(args, 1, args.length));
            }
        }
        return List.of();
    }

    private SubCommand sub(String name, String action,
                           BiConsumer<CommandSender, String[]> execute,
                           BiFunction<CommandSender, String[], List<String>> tab) {
        return new Sub(name, "militarycraft.artillery." + action, execute, tab);
    }

    private void give(CommandSender sender, String[] args) {
        Player target = args.length >= 1 ? CommandArgs.resolvePlayer(args[0]) : CommandArgs.player(sender);
        if (target == null) {
            ArtilleryMessages.send(sender, args.length >= 1
                    ? "&cPlayer not found." : "&cUsage: /mc artillery give <player>");
            return;
        }
        CommandArgs.giveItem(target, ArtilleryItem.create(manager.core().items()));
        ArtilleryMessages.send(sender, "&aGave " + ArtilleryMessages.NAME
                + " to &f" + target.getName() + "&a.");
    }

    private void create(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }
        Block lookedAt = player.getTargetBlockExact(8);
        if (lookedAt == null) {
            ArtilleryMessages.send(sender, "&cLook at a block within 8 blocks.");
            return;
        }
        createAt(sender, lookedAt.getRelative(BlockFace.UP).getLocation(), player.getLocation().getYaw());
    }

    private void spawn(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }
        Location eye = player.getLocation();
        Vector forward = eye.getDirection().setY(0.0);
        if (forward.lengthSquared() < 1.0e-4) {
            forward.setZ(1.0);
        }
        Location probe = eye.clone().add(forward.normalize().multiply(4.0));
        int x = probe.getBlockX();
        int z = probe.getBlockZ();
        int y = player.getWorld().getHighestBlockYAt(x, z) + 1;
        createAt(sender, new Location(player.getWorld(), x, y, z), player.getLocation().getYaw());
    }

    private void place(CommandSender sender, String[] args) {
        if (args.length < 3) {
            ArtilleryMessages.send(sender, "&cUsage: /mc artillery place <x> <y> <z> [world]");
            return;
        }
        Player player = CommandArgs.player(sender);
        Location base = player == null ? null : player.getLocation();
        Double x = CommandArgs.coord(args[0], base == null ? 0.0 : base.getX(), player != null);
        Double y = CommandArgs.coord(args[1], base == null ? 0.0 : base.getY(), player != null);
        Double z = CommandArgs.coord(args[2], base == null ? 0.0 : base.getZ(), player != null);
        if (!finite(x, y, z)) {
            ArtilleryMessages.send(sender, "&cCoordinates must be finite numbers.");
            return;
        }
        World world;
        if (args.length >= 4) {
            world = Bukkit.getWorld(args[3]);
        } else if (player != null) {
            world = player.getWorld();
        } else {
            world = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
        }
        if (world == null) {
            ArtilleryMessages.send(sender, "&cWorld not found.");
            return;
        }
        Location location = new Location(world, Math.floor(x), Math.floor(y), Math.floor(z));
        if (location.getBlockY() < world.getMinHeight() || location.getBlockY() >= world.getMaxHeight()) {
            ArtilleryMessages.send(sender, "&cY is outside the buildable world height.");
            return;
        }
        location.getChunk().load();
        createAt(sender, location, player == null ? 0.0f : player.getLocation().getYaw());
    }

    private void enter(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }
        Artillery artillery = manager.selectedOrNearest(player, 8.0);
        if (artillery == null) {
            ArtilleryMessages.send(sender, "&cNo artillery is selected or within 8 blocks.");
            return;
        }
        manager.sessions().open(player, artillery);
    }

    private void fire(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }
        if (args.length != 2) {
            ArtilleryMessages.send(sender, "&cUsage: /mc artillery fire <x> <z>");
            return;
        }
        Double x = ArtilleryTargetValidator.parseFinite(args[0]);
        Double z = ArtilleryTargetValidator.parseFinite(args[1]);
        if (x == null || z == null) {
            ArtilleryMessages.send(sender, "&cX and Z must be finite real numbers.");
            return;
        }
        ArtilleryManager.FireResult result = manager.fire(player, x, z);
        ArtilleryMessages.send(sender, result.message());
    }

    private void exit(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }
        if (!manager.sessions().active(player) && !manager.sessions().hasPending(player)) {
            ArtilleryMessages.send(sender, "&cYou are not operating artillery.");
            return;
        }
        if (manager.sessions().close(player)) {
            ArtilleryMessages.send(sender, "&aArtillery session closed.");
        } else {
            ArtilleryMessages.send(sender, "&cYour saved location is temporarily unavailable; recovery remains pending.");
        }
    }

    private void rotate(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }
        Artillery artillery = manager.selectedOrNearest(player, 8.0);
        if (artillery == null) {
            ArtilleryMessages.send(sender, "&cNo artillery is selected or within 8 blocks.");
            return;
        }
        float yaw = player.getLocation().getYaw();
        if (args.length >= 1) {
            Double parsed = ArtilleryTargetValidator.parseFinite(args[0]);
            if (parsed == null || parsed < -Float.MAX_VALUE || parsed > Float.MAX_VALUE) {
                ArtilleryMessages.send(sender, "&cYaw must be a finite number.");
                return;
            }
            yaw = parsed.floatValue();
        }
        if (!manager.rotate(artillery, yaw)) {
            ArtilleryMessages.send(sender, "&cThe new artillery rotation could not be persisted.");
            return;
        }
        ArtilleryMessages.send(sender, "&aArtillery rotated to &e"
                + String.format(Locale.ROOT, "%.1f", yaw) + "&a degrees.");
    }

    private void remove(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }
        Artillery artillery = manager.selectedOrNearest(player, 12.0);
        if (artillery == null) {
            ArtilleryMessages.send(sender, "&cNo artillery is selected or within 12 blocks.");
            return;
        }
        if (manager.remove(artillery, true)) {
            ArtilleryMessages.send(sender, "&aArtillery removed.");
        } else {
            ArtilleryMessages.send(sender, "&cArtillery removal could not be persisted.");
        }
    }

    private void list(CommandSender sender, String[] args) {
        ArtilleryMessages.send(sender, "&aRegistered artillery: &f" + manager.all().size());
        for (Artillery artillery : manager.all()) {
            ArtilleryMessages.send(sender, "&7" + artillery.worldName() + " &f"
                    + artillery.x() + " " + artillery.y() + " " + artillery.z()
                    + " &8| &7ammo &f" + artillery.ammo()
                    + " &8| &7health &f" + artillery.health() + "/" + manager.settings().maxHits);
        }
    }

    private void cleanup(CommandSender sender, String[] args) {
        int removed = manager.cleanup();
        ArtilleryMessages.send(sender, "&aCleanup complete. Removed &f" + removed
                + "&a stale installation(s) and rebuilt loaded models.");
    }

    private void createAt(CommandSender sender, Location location, float yaw) {
        if (!manager.canPlace(location, yaw)) {
            ArtilleryMessages.send(sender, "&cThat block cannot hold artillery.");
            return;
        }
        Artillery artillery = manager.create(location, yaw);
        if (artillery == null) {
            ArtilleryMessages.send(sender, "&cArtillery creation failed.");
            return;
        }
        ArtilleryMessages.send(sender, "&aCreated " + ArtilleryMessages.NAME + " at &f"
                + artillery.x() + " " + artillery.y() + " " + artillery.z()
                + " &7(" + artillery.worldName() + ").");
    }

    private Player requirePlayer(CommandSender sender) {
        Player player = CommandArgs.player(sender);
        if (player == null) {
            ArtilleryMessages.send(sender, "&cThis action requires a player.");
        }
        return player;
    }

    private boolean canUseDirect(CommandSender sender, SubCommand sub) {
        return canUseDirect(sender, sub.name());
    }

    private boolean canUseDirect(CommandSender sender, String action) {
        if (manager.core().commands().contextActionAllowed(sender, "artillery", action, new String[0])) {
            return true;
        }
        return sender.hasPermission("militarycraft.admin")
                || sender.hasPermission("svoart.admin")
                || sender.hasPermission("militarycraft.artillery." + action)
                || (sender.hasPermission("svoart.use") && originalUseAction(action));
    }

    private boolean originalUseAction(String action) {
        return "enter".equals(action) || "fire".equals(action) || "exit".equals(action);
    }

    private void deny(CommandSender sender) {
        ArtilleryMessages.send(sender, "&cYou do not have permission.");
    }

    private boolean finite(Double... values) {
        for (Double value : values) {
            if (value == null || !Double.isFinite(value)) {
                return false;
            }
        }
        return true;
    }

    private List<String> tabPlayers(CommandSender sender, String[] args) {
        if (args.length != 1) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            names.add(player.getName());
        }
        return names;
    }

    private List<String> tabPlace(CommandSender sender, String[] args) {
        if (args.length >= 1 && args.length <= 3) {
            return List.of("~");
        }
        if (args.length == 4) {
            return Bukkit.getWorlds().stream().map(World::getName).toList();
        }
        return List.of();
    }

    private record Sub(String name, String permission,
                       BiConsumer<CommandSender, String[]> execute,
                       BiFunction<CommandSender, String[], List<String>> tab) implements SubCommand {
        @Override
        public void execute(CommandSender sender, String[] args) {
            execute.accept(sender, args);
        }

        @Override
        public List<String> tabComplete(CommandSender sender, String[] args) {
            return tab == null ? List.of() : tab.apply(sender, args);
        }
    }
}
