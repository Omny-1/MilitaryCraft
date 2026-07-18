package me.bibo.militarycraft.core.command;

import me.bibo.militarycraft.MilitaryCraftPlugin;
import me.bibo.militarycraft.core.text.Text;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerCommandSendEvent;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Config-driven command gate for non-op players, including contextual gameplay exceptions. */
public final class CommandAccess implements Listener {

    private static final String CONFIG = "command-access";
    private static final String ROOT_ACTION = "__root";
    private static final Set<String> BUILTIN_MC_ACTIONS = Set.of("menu", "reload", "modules", "cleanup");
    private static final Set<String> SHOOT_COMMANDS = Set.of("shoot", "arta");
    private static final Map<String, String> DIRECT_MODULES = directModules();

    private final MilitaryCraftPlugin plugin;
    private final Map<String, ContextRule> contextRules = new HashMap<>();

    public CommandAccess(MilitaryCraftPlugin plugin) {
        this.plugin = plugin;
    }

    @FunctionalInterface
    public interface ContextRule {
        boolean allowed(Player player, String[] args);
    }

    public void registerContextAction(String moduleId, String action, ContextRule rule) {
        contextRules.put(key(moduleId, action), rule);
    }

    public void unregisterContextAction(String moduleId, String action) {
        contextRules.remove(key(moduleId, action));
    }

    public boolean canUse(CommandSender sender, String moduleId, String action, String permission, String[] args) {
        if (!(sender instanceof Player player)) {
            return hasPermission(sender, permission);
        }
        if (privileged(player)) {
            return true;
        }
        if (allowNonOpCommands()) {
            return hasPermission(player, permission);
        }
        return listedContextActionAllowed(player, moduleId, action, args);
    }

    public boolean contextActionAllowed(CommandSender sender, String moduleId, String action, String[] args) {
        return sender instanceof Player player
                && !privileged(player)
                && !allowNonOpCommands()
                && listedContextActionAllowed(player, moduleId, action, args);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        ActionRef action = parse(event.getMessage());
        if (action == null || canUse(event.getPlayer(), action.moduleId(), action.action(), null, action.args())) {
            return;
        }
        event.setCancelled(true);
        Text.msg(event.getPlayer(), denyMessage());
    }

    @EventHandler
    public void onCommandSend(PlayerCommandSendEvent event) {
        Player player = event.getPlayer();
        if (privileged(player) || allowNonOpCommands()) {
            return;
        }
        event.getCommands().removeIf(command -> {
            String lower = plainRoot(command.toLowerCase(Locale.ROOT));
            if ("help".equals(lower)) {
                return false;
            }
            return knownRoot(lower) && !rootVisible(player, lower);
        });
    }

    private boolean rootVisible(Player player, String root) {
        if ("mc".equals(root) || "militarycraft".equals(root)) {
            return allowedActions().stream().anyMatch(action -> listedContextActionAllowed(player, action));
        }
        ActionRef direct = parse("/" + root);
        if (direct != null && listedContextActionAllowed(player, direct.moduleId(), direct.action(), direct.args())) {
            return true;
        }
        String moduleId = DIRECT_MODULES.get(root);
        if (moduleId == null) {
            return false;
        }
        String prefix = moduleId + ".";
        return allowedActions().stream()
                .filter(action -> action.startsWith(prefix))
                .anyMatch(action -> listedContextActionAllowed(player, action));
    }

    private boolean listedContextActionAllowed(Player player, String moduleId, String action, String[] args) {
        return listedContextActionAllowed(player, key(moduleId, action), args);
    }

    private boolean listedContextActionAllowed(Player player, String key) {
        return listedContextActionAllowed(player, key, new String[0]);
    }

    private boolean listedContextActionAllowed(Player player, String key, String[] args) {
        if (!allowedActions().contains(key)) {
            return false;
        }
        ContextRule rule = contextRules.get(key);
        return rule == null || rule.allowed(player, args == null ? new String[0] : args);
    }

    private boolean privileged(Player player) {
        return player.isOp() || (honorAdminPermission() && player.hasPermission("militarycraft.admin"));
    }

    private boolean hasPermission(CommandSender sender, String permission) {
        return permission == null || permission.isBlank() || sender.hasPermission(permission);
    }

    private boolean allowNonOpCommands() {
        return plugin.getConfig().getBoolean(CONFIG + ".allow-non-op-commands", false);
    }

    private boolean honorAdminPermission() {
        return plugin.getConfig().getBoolean(CONFIG + ".honor-admin-permission", true);
    }

    private String denyMessage() {
        return plugin.getConfig().getString(CONFIG + ".deny-message",
                "&cOnly operators can use MilitaryCraft commands.");
    }

    private Set<String> allowedActions() {
        Set<String> actions = new HashSet<>();
        String path = CONFIG + ".allowed-non-op-actions";
        if (!plugin.getConfig().isSet(path)) {
            actions.addAll(Set.of("artillery.enter", "artillery.fire", "artillery.exit",
                    "drone.fire", "drone.exit"));
            return actions;
        }
        for (String raw : plugin.getConfig().getStringList(path)) {
            String normalized = normalizeAction(raw);
            if (!normalized.isBlank()) {
                actions.add(normalized);
            }
        }
        return actions;
    }

    private static ActionRef parse(String message) {
        if (message == null || message.isBlank()) {
            return null;
        }
        String text = message.charAt(0) == '/' ? message.substring(1) : message;
        String[] tokens = Arrays.stream(text.trim().split("\\s+"))
                .filter(token -> !token.isBlank())
                .toArray(String[]::new);
        if (tokens.length == 0) {
            return null;
        }
        String root = plainRoot(tokens[0].toLowerCase(Locale.ROOT));
        if ("mc".equals(root) || "militarycraft".equals(root)) {
            return parseMc(tokens);
        }
        if (SHOOT_COMMANDS.contains(root)) {
            String action = tokens.length >= 2 && "exit".equalsIgnoreCase(tokens[1]) ? "exit" : "fire";
            return new ActionRef("artillery", action, Arrays.copyOfRange(tokens, 1, tokens.length));
        }
        String module = DIRECT_MODULES.get(root);
        if (module == null) {
            return null;
        }
        String action = tokens.length >= 2 ? tokens[1].toLowerCase(Locale.ROOT) : ROOT_ACTION;
        return new ActionRef(module, action, Arrays.copyOfRange(tokens, Math.min(tokens.length, 2), tokens.length));
    }

    private static ActionRef parseMc(String[] tokens) {
        if (tokens.length == 1) {
            return new ActionRef("mc", ROOT_ACTION, new String[0]);
        }
        String first = tokens[1].toLowerCase(Locale.ROOT);
        if (BUILTIN_MC_ACTIONS.contains(first)) {
            return new ActionRef("mc", first, Arrays.copyOfRange(tokens, 2, tokens.length));
        }
        String action = tokens.length >= 3 ? tokens[2].toLowerCase(Locale.ROOT) : ROOT_ACTION;
        return new ActionRef(first, action, Arrays.copyOfRange(tokens, Math.min(tokens.length, 3), tokens.length));
    }

    private static boolean knownRoot(String root) {
        return "mc".equals(root) || "militarycraft".equals(root)
                || SHOOT_COMMANDS.contains(root)
                || DIRECT_MODULES.containsKey(root);
    }

    private static String plainRoot(String root) {
        int colon = root.indexOf(':');
        return colon >= 0 ? root.substring(colon + 1) : root;
    }

    private static String normalizeAction(String raw) {
        return raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
    }

    private static String key(String moduleId, String action) {
        return normalizeAction(moduleId) + "." + normalizeAction(action);
    }

    private static Map<String, String> directModules() {
        Map<String, String> map = new HashMap<>();
        putAll(map, "tank", "tank");
        putAll(map, "kamaz", "kamaz", "pushinka", "kamazcraft");
        putAll(map, "jet", "jet");
        putAll(map, "nuke", "nuke", "nukestrike", "yaderka", "yadernyudar");
        putAll(map, "tckbus", "tck", "tckbus", "bus");
        putAll(map, "helicopter", "helicopter", "heli", "verto", "vertolet");
        putAll(map, "airship", "airship", "dirizhabl", "zeppelin", "dirigible");
        putAll(map, "drone", "drone", "bpla", "uav");
        putAll(map, "moto", "moto", "motorcycle", "bike", "motocraft");
        putAll(map, "pickup", "pickup");
        putAll(map, "train", "train", "poezd");
        putAll(map, "antiair", "pvo", "antiair", "aa", "ciws", "flak");
        putAll(map, "airstrike", "airstrike", "strike", "aviaudar");
        putAll(map, "camera", "vehiclecamera", "vcam");
        putAll(map, "warkit", "warkit", "wk");
        putAll(map, "artillery", "artillery", "artillert");
        return Map.copyOf(map);
    }

    private static void putAll(Map<String, String> map, String module, String... roots) {
        for (String root : roots) {
            map.put(root, module);
        }
    }

    private record ActionRef(String moduleId, String action, String[] args) {
    }
}
