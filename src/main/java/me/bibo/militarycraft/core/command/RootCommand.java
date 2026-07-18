package me.bibo.militarycraft.core.command;

import me.bibo.militarycraft.MilitaryCraftPlugin;
import me.bibo.militarycraft.core.module.MilitaryModule;
import me.bibo.militarycraft.core.text.Text;
import me.bibo.militarycraft.core.vehicle.VehicleService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabExecutor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The single {@code /mc} command (§3.4). Handles {@code reload|modules|cleanup} itself and
 * dispatches {@code /mc <module> <sub> ...} to whatever {@link SubCommand} group that module
 * registered via {@code core.commands().register(...)}.
 */
public final class RootCommand implements TabExecutor {

    private final MilitaryCraftPlugin plugin;
    private final CommandAccess access;
    private final Map<String, List<SubCommand>> moduleCommands = new LinkedHashMap<>();
    private final CommandMenu menu;

    public RootCommand(MilitaryCraftPlugin plugin, CommandAccess access) {
        this.plugin = plugin;
        this.access = access;
        this.menu = new CommandMenu(plugin, this);
        plugin.getServer().getPluginManager().registerEvents(menu, plugin);
    }

    public void register(String moduleId, List<SubCommand> subCommands) {
        moduleCommands.put(moduleId.toLowerCase(Locale.ROOT), List.copyOf(subCommands));
    }

    public void unregister(String moduleId) {
        moduleCommands.remove(moduleId.toLowerCase(Locale.ROOT));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            Text.msg(sender, hasAdmin(sender)
                    ? "&a/mc menu|reload|modules|cleanup|<module> <sub> ..."
                    : "&a/mc menu|<module> <subcommand> ...");
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "menu" -> openMenu(sender);
            case "reload" -> reload(sender);
            case "modules" -> modules(sender);
            case "cleanup" -> cleanup(sender);
            default -> dispatchModule(sender, args);
        }
        return true;
    }

    private void openMenu(CommandSender sender) {
        if (!(sender instanceof org.bukkit.entity.Player player)) {
            Text.msg(sender, "&cOnly players can use the graphical menu.");
            return;
        }
        if (!access.canUse(sender, "mc", "menu", "militarycraft.menu", new String[0])) {
            deny(sender);
            return;
        }
        menu.openMain(player);
    }

    private void reload(CommandSender sender) {
        if (!hasAdmin(sender)) {
            deny(sender);
            return;
        }
        java.util.List<String> failed = plugin.reloadAll();
        if (failed.isEmpty()) {
            Text.msg(sender, "&aConfiguration and modules reloaded.");
        } else {
            Text.msg(sender, "&eReloaded, but these modules reported errors: &f"
                    + String.join(", ", failed) + " &7(see console for details).");
        }
    }

    private void modules(CommandSender sender) {
        if (!hasAdmin(sender)) {
            deny(sender);
            return;
        }
        List<MilitaryModule> mods = plugin.moduleManager().modules();
        if (mods.isEmpty()) {
            Text.msg(sender, "&7No modules are registered.");
            return;
        }
        for (MilitaryModule m : mods) {
            boolean on = plugin.moduleManager().isActive(m.id());
            Text.msg(sender, (on ? "&a" : "&c") + m.id() + (on ? " - enabled" : " - disabled"));
        }
    }

    private void cleanup(CommandSender sender) {
        if (!hasAdmin(sender)) {
            deny(sender);
            return;
        }
        VehicleService.PurgeResult removed = plugin.core().vehicles().purgeAll();
        Text.msg(sender, "&aCleanup complete: removed &f" + removed.tracked()
                + "&a tracked vehicles and &f" + removed.strays() + "&a stray entities.");
    }

    private void dispatchModule(CommandSender sender, String[] args) {
        String moduleId = args[0].toLowerCase(Locale.ROOT);
        List<SubCommand> subs = moduleCommands.get(moduleId);
        if (subs == null) {
            Text.msg(sender, "&cUnknown module or command: &f" + args[0]);
            return;
        }
        if (args.length < 2) {
            Text.msg(sender, "&cUsage: /mc " + moduleId + " <subcommand> ...");
            return;
        }
        String subName = args[1];
        for (SubCommand sub : subs) {
            if (sub.name().equalsIgnoreCase(subName)) {
                String[] subArgs = Arrays.copyOfRange(args, 2, args.length);
                if (!canUse(sender, moduleId, sub, subArgs)) {
                    deny(sender);
                    return;
                }
                sub.execute(sender, subArgs);
                return;
            }
        }
        Text.msg(sender, "&cUnknown subcommand: &f" + subName);
    }

    private boolean hasAdmin(CommandSender sender) {
        return sender.hasPermission("militarycraft.admin");
    }

    private void deny(CommandSender sender) {
        Text.msg(sender, "&cYou do not have permission.");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> out = new ArrayList<>();
            if (hasAdmin(sender)) {
                out.addAll(List.of("menu", "reload", "modules", "cleanup"));
            } else if (access.canUse(sender, "mc", "menu", "militarycraft.menu", new String[0])) {
                out.add("menu");
            }
            out.addAll(visibleModules(sender));
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return out.stream().filter(s -> s.startsWith(prefix)).toList();
        }
        List<SubCommand> subs = moduleCommands.get(args[0].toLowerCase(Locale.ROOT));
        if (subs == null) {
            return List.of();
        }
        if (args.length == 2) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            return subs.stream()
                    .filter(sub -> canUse(sender, args[0], sub, new String[0]))
                    .map(SubCommand::name)
                    .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(prefix))
                    .toList();
        }
        for (SubCommand sub : subs) {
            String[] subArgs = Arrays.copyOfRange(args, 2, args.length);
            if (sub.name().equalsIgnoreCase(args[1]) && canUse(sender, args[0], sub, subArgs)) {
                return sub.tabComplete(sender, subArgs);
            }
        }
        return List.of();
    }

    private boolean canUse(CommandSender sender, String moduleId, SubCommand sub, String[] args) {
        return access.canUse(sender, moduleId, sub.name(), sub.permission(), args);
    }

    public CommandAccess access() {
        return access;
    }

    public boolean contextActionAllowed(CommandSender sender, String moduleId, String action, String[] args) {
        return access.contextActionAllowed(sender, moduleId, action, args);
    }

    List<String> visibleModules(CommandSender sender) {
        return moduleCommands.entrySet().stream()
                .filter(entry -> entry.getValue().stream()
                        .anyMatch(sub -> canUse(sender, entry.getKey(), sub, new String[0])))
                .map(Map.Entry::getKey)
                .toList();
    }

    List<SubCommand> visibleSubCommands(CommandSender sender, String moduleId) {
        List<SubCommand> subs = moduleCommands.get(moduleId.toLowerCase(Locale.ROOT));
        if (subs == null) {
            return List.of();
        }
        return subs.stream().filter(sub -> canUse(sender, moduleId, sub, new String[0])).toList();
    }

    SubCommand subCommand(String moduleId, String subName) {
        List<SubCommand> subs = moduleCommands.get(moduleId.toLowerCase(Locale.ROOT));
        if (subs == null) {
            return null;
        }
        for (SubCommand sub : subs) {
            if (sub.name().equalsIgnoreCase(subName)) {
                return sub;
            }
        }
        return null;
    }

    boolean executeFromMenu(org.bukkit.entity.Player player, String moduleId, String subName, String[] args) {
        SubCommand sub = subCommand(moduleId, subName);
        if (sub == null && !moduleCommands.containsKey(moduleId.toLowerCase(Locale.ROOT))) {
            Text.msg(player, "&cUnknown module: &f" + moduleId);
            return false;
        }
        if (sub == null) {
            Text.msg(player, "&cUnknown subcommand: &f" + subName);
            return false;
        }
        if (!canUse(player, moduleId, sub, args)) {
            deny(player);
            return false;
        }
        PluginCommand root = plugin.getCommand("mc");
        if (root == null) {
            Text.msg(player, "&cThe /mc command is not registered.");
            return false;
        }
        String[] commandArgs = new String[args.length + 2];
        commandArgs[0] = moduleId;
        commandArgs[1] = subName;
        System.arraycopy(args, 0, commandArgs, 2, args.length);
        plugin.getLogger().info(player.getName() + " used menu action: /mc "
                + String.join(" ", commandArgs));
        return root.execute(player, "mc", commandArgs);
    }

    boolean executeBuiltinFromMenu(org.bukkit.entity.Player player, String name) {
        if (!hasAdmin(player)) {
            deny(player);
            return false;
        }
        switch (name) {
            case "reload" -> reload(player);
            case "modules" -> modules(player);
            case "cleanup" -> cleanup(player);
            default -> {
                Text.msg(player, "&cUnknown menu action: &f" + name);
                return false;
            }
        }
        return true;
    }
}
