package me.bibo.militarycraft.gear.warkit;

import me.bibo.militarycraft.core.command.SubCommand;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** /warkit list | give <item> [player] [amount] | giveall [player] | reload */
public final class WarKitCommand implements TabExecutor {

    private final WarKitRuntime plugin;

    public WarKitCommand(WarKitRuntime plugin) {
        this.plugin = plugin;
    }

    public List<SubCommand> all() {
        return List.of(
                new RootSub("list", "warkit.admin", (sender, args) -> list(sender),
                        (sender, args) -> List.of()),
                new RootSub("give", "warkit.admin", (sender, args) -> give(sender, prefixed("give", args)),
                        (sender, args) -> tab("give", sender, args)),
                new RootSub("giveall", "warkit.admin", (sender, args) -> giveAll(sender, prefixed("giveall", args)),
                        (sender, args) -> tab("giveall", sender, args)),
                new RootSub("reload", "warkit.admin", (sender, args) -> reload(sender),
                        (sender, args) -> List.of()));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) return false;
        switch (args[0].toLowerCase()) {
            case "list" -> list(sender);
            case "give" -> give(sender, args);
            case "giveall" -> giveAll(sender, args);
            case "reload" -> reload(sender);
            default -> {
                return false;
            }
        }
        return true;
    }

    private void reload(CommandSender sender) {
        plugin.bukkitPlugin().reloadAll();
        sender.sendMessage(Txt.t("WarKit: config reloaded. New values apply to newly created items.",
                NamedTextColor.GREEN));
    }

    private void list(CommandSender sender) {
        sender.sendMessage(Txt.t("WarKit items:", NamedTextColor.GOLD));
        for (String id : plugin.items().ids()) {
            ItemStack it = plugin.items().create(id);
            Component name = it.getItemMeta().displayName();
            sender.sendMessage(Txt.t("  " + id + " - ", NamedTextColor.GRAY)
                    .append(name == null ? Component.text(id) : name));
        }
    }

    private void give(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Txt.t("Usage: /warkit give <item> [player] [amount]",
                    NamedTextColor.RED));
            return;
        }
        String id = args[1].toLowerCase();
        if (!plugin.items().ids().contains(id)) {
            sender.sendMessage(Txt.t("Unknown item: " + id + " (see /warkit list)",
                    NamedTextColor.RED));
            return;
        }
        Player target = resolveTarget(sender, args, 2);
        if (target == null) return;

        int amount = 1;
        if (args.length >= 4) {
            try {
                amount = Math.max(1, Math.min(64, Integer.parseInt(args[3])));
            } catch (NumberFormatException ex) {
                sender.sendMessage(Txt.t("Amount must be a number", NamedTextColor.RED));
                return;
            }
        }

        int remaining = amount;
        while (remaining > 0) {
            ItemStack stack = plugin.items().create(id);
            int take = Math.min(remaining, stack.getMaxStackSize());
            stack.setAmount(take);
            target.getInventory().addItem(stack).values()
                    .forEach(left -> target.getWorld().dropItemNaturally(target.getLocation(), left));
            remaining -= take;
        }
        sender.sendMessage(Txt.t("Gave: " + id + " x" + amount + " -> " + target.getName(),
                NamedTextColor.GREEN));
    }

    private void giveAll(CommandSender sender, String[] args) {
        Player target = resolveTarget(sender, args, 1);
        if (target == null) return;
        for (String id : plugin.items().ids()) {
            ItemStack stack = plugin.items().create(id);
            target.getInventory().addItem(stack).values()
                    .forEach(left -> target.getWorld().dropItemNaturally(target.getLocation(), left));
        }
        sender.sendMessage(Txt.t("Full kit given -> " + target.getName(), NamedTextColor.GREEN));
    }

    private Player resolveTarget(CommandSender sender, String[] args, int index) {
        if (args.length > index) {
            Player target = Bukkit.getPlayerExact(args[index]);
            if (target == null) {
                sender.sendMessage(Txt.t("Player not found: " + args[index], NamedTextColor.RED));
            }
            return target;
        }
        if (sender instanceof Player p) return p;
        sender.sendMessage(Txt.t("Console must specify a player", NamedTextColor.RED));
        return null;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            for (String sub : List.of("list", "give", "giveall", "reload")) {
                if (sub.startsWith(args[0].toLowerCase())) out.add(sub);
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            for (String id : plugin.items().ids()) {
                if (id.startsWith(args[1].toLowerCase())) out.add(id);
            }
        } else if ((args.length == 3 && args[0].equalsIgnoreCase("give"))
                || (args.length == 2 && args[0].equalsIgnoreCase("giveall"))) {
            String prefix = args[args.length - 1].toLowerCase();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(prefix)) out.add(p.getName());
            }
        }
        return out;
    }

    private List<String> tab(String sub, CommandSender sender, String[] args) {
        return onTabComplete(sender, null, "warkit", prefixed(sub, args));
    }

    private static String[] prefixed(String sub, String[] args) {
        String[] out = new String[args.length + 1];
        out[0] = sub;
        System.arraycopy(args, 0, out, 1, args.length);
        return out;
    }

    @FunctionalInterface
    private interface RootExec {
        void run(CommandSender sender, String[] args);
    }

    @FunctionalInterface
    private interface RootTab {
        List<String> run(CommandSender sender, String[] args);
    }

    private record RootSub(String name, String permission, RootExec exec, RootTab tab) implements SubCommand {
        @Override
        public void execute(CommandSender sender, String[] args) {
            exec.run(sender, args);
        }

        @Override
        public List<String> tabComplete(CommandSender sender, String[] args) {
            return tab.run(sender, args);
        }
    }
}
