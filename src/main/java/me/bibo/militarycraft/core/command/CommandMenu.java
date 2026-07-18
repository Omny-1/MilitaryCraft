package me.bibo.militarycraft.core.command;

import me.bibo.militarycraft.MilitaryCraftPlugin;
import me.bibo.militarycraft.core.text.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class CommandMenu implements Listener {

    private static final String RUN_CONTROL = "__run";
    private static final int SIZE = 54;
    private static final int BACK_SLOT = 45;
    private static final int RUN_SLOT = 49;
    private static final int TYPE_SLOT = 49;
    private static final int CLEAR_SLOT = 51;
    private static final int CLOSE_SLOT = 53;
    private static final int[] CONTENT_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };
    private static final int[] PARAM_SLOTS = {10, 12, 14, 16, 19, 21, 23, 25};

    private final MilitaryCraftPlugin plugin;
    private final RootCommand root;
    private final Map<UUID, PendingPrompt> prompts = new ConcurrentHashMap<>();
    private final Set<UUID> pendingRuns = ConcurrentHashMap.newKeySet();

    CommandMenu(MilitaryCraftPlugin plugin, RootCommand root) {
        this.plugin = plugin;
        this.root = root;
    }

    void openMain(Player player) {
        Holder holder = Holder.main();
        Inventory inventory = Bukkit.createInventory(holder, SIZE, Text.of("&0MilitaryCraft Menu"));
        holder.inventory = inventory;
        int slotIndex = 0;
        if (player.hasPermission("militarycraft.admin")) {
            for (String action : List.of("modules", "reload", "cleanup")) {
                if (slotIndex >= CONTENT_SLOTS.length) {
                    break;
                }
                inventory.setItem(CONTENT_SLOTS[slotIndex++], actionButton(action));
            }
        }
        for (String module : root.visibleModules(player)) {
            if (slotIndex >= CONTENT_SLOTS.length) {
                break;
            }
            inventory.setItem(CONTENT_SLOTS[slotIndex++], moduleButton(module));
        }
        inventory.setItem(CLOSE_SLOT, button(Material.BARRIER, "Close", NamedTextColor.RED,
                List.of("Close this menu.")));
        player.openInventory(inventory);
    }

    private void openModule(Player player, String module) {
        Holder holder = Holder.module(module);
        Inventory inventory = Bukkit.createInventory(holder, SIZE, Text.of("&0/mc " + module));
        holder.inventory = inventory;
        int slotIndex = 0;
        for (SubCommand sub : root.visibleSubCommands(player, module)) {
            if (slotIndex >= CONTENT_SLOTS.length) {
                break;
            }
            inventory.setItem(CONTENT_SLOTS[slotIndex++], subButton(module, sub));
        }
        inventory.setItem(BACK_SLOT, button(Material.ARROW, "Back", NamedTextColor.YELLOW,
                List.of("Return to the module list.")));
        inventory.setItem(CLOSE_SLOT, button(Material.BARRIER, "Close", NamedTextColor.RED,
                List.of("Close this menu.")));
        player.openInventory(inventory);
    }

    private void openAction(Player player, String module, SubCommand sub, ActionSpec spec, String[] values) {
        Holder holder = Holder.action(module, sub.name(), spec, values);
        Inventory inventory = Bukkit.createInventory(holder, SIZE, Text.of("&0/mc " + module + " " + sub.name()));
        holder.inventory = inventory;
        inventory.setItem(4, button(spec.icon(), display(module) + " / " + display(sub.name()), NamedTextColor.AQUA,
                List.of(spec.description(), "Command: /mc " + module + " " + sub.name())));
        for (int i = 0; i < spec.params().size() && i < PARAM_SLOTS.length; i++) {
            ParamSpec param = spec.params().get(i);
            inventory.setItem(PARAM_SLOTS[i], parameterButton(param, values[i], choices(player, sub, spec, values, i)));
        }
        inventory.setItem(BACK_SLOT, button(Material.ARROW, "Back", NamedTextColor.YELLOW,
                List.of("Return to " + display(module) + ".")));
        inventory.setItem(RUN_SLOT, valueButton(Material.LIME_CONCRETE, "Run Command", NamedTextColor.GREEN,
                runLore(player, module, sub.name(), spec, values), RUN_CONTROL));
        inventory.setItem(CLOSE_SLOT, button(Material.BARRIER, "Close", NamedTextColor.RED,
                List.of("Close this menu.")));
        player.openInventory(inventory);
    }

    private void openChoices(Player player, Holder source, int paramIndex) {
        SubCommand sub = subCommand(source.module, source.sub);
        if (sub == null || source.spec == null) {
            openMain(player);
            return;
        }
        List<String> choices = choices(player, sub, source.spec, source.values, paramIndex);
        if (choices.isEmpty()) {
            prompt(player, source, paramIndex);
            return;
        }
        Holder holder = Holder.choices(source.module, source.sub, source.spec, source.values, paramIndex);
        Inventory inventory = Bukkit.createInventory(holder, SIZE,
                Text.of("&0Choose " + source.spec.params().get(paramIndex).name()));
        holder.inventory = inventory;
        int index = 0;
        for (String choice : choices.stream().distinct().limit(CONTENT_SLOTS.length).toList()) {
            inventory.setItem(CONTENT_SLOTS[index++], valueButton(Material.PAPER, choice, NamedTextColor.WHITE,
                    List.of("Use this value."), choice));
        }
        inventory.setItem(BACK_SLOT, button(Material.ARROW, "Back", NamedTextColor.YELLOW,
                List.of("Return to the command screen.")));
        inventory.setItem(TYPE_SLOT, button(Material.WRITABLE_BOOK, "Type Custom Value", NamedTextColor.AQUA,
                List.of("Close the menu and type a custom value in chat.")));
        inventory.setItem(CLEAR_SLOT, button(Material.GLASS_PANE, "Clear Value", NamedTextColor.GRAY,
                List.of("Remove this parameter value.")));
        inventory.setItem(CLOSE_SLOT, button(Material.BARRIER, "Close", NamedTextColor.RED,
                List.of("Close this menu.")));
        player.openInventory(inventory);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof Holder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        int slot = event.getRawSlot();
        if (slot == CLOSE_SLOT) {
            player.closeInventory();
            return;
        }
        switch (holder.view) {
            case MAIN -> clickMain(player, slot);
            case MODULE -> clickModule(player, holder, slot);
            case ACTION -> clickAction(player, holder, slot, event.isRightClick(), valueOf(event.getCurrentItem()));
            case CHOICES -> clickChoices(player, holder, slot);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof Holder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onChat(AsyncChatEvent event) {
        PendingPrompt pending = prompts.remove(event.getPlayer().getUniqueId());
        if (pending == null) {
            return;
        }
        event.setCancelled(true);
        String message = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
        Bukkit.getScheduler().runTask(plugin, () -> {
            Player player = event.getPlayer();
            if ("cancel".equalsIgnoreCase(message)) {
                reopenAction(player, pending.module(), pending.sub(), pending.spec(), pending.values());
                return;
            }
            String[] values = pending.values().clone();
            values[pending.paramIndex()] = message;
            reopenAction(player, pending.module(), pending.sub(), pending.spec(), values);
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        prompts.remove(event.getPlayer().getUniqueId());
    }

    private void reopenAction(Player player, String module, String subName, ActionSpec spec, String[] values) {
        SubCommand sub = subCommand(module, subName);
        if (sub == null) {
            Text.msg(player, "&cThat command is no longer available.");
            openMain(player);
            return;
        }
        openAction(player, module, sub, spec, values);
    }

    private void clickMain(Player player, int slot) {
        String value = valueAt(player.getOpenInventory().getTopInventory(), slot);
        if (value == null) {
            return;
        }
        if (List.of("modules", "reload", "cleanup").contains(value)) {
            runAfterClick(player, () -> root.executeBuiltinFromMenu(player, value));
            return;
        }
        openModule(player, value);
    }

    private void clickModule(Player player, Holder holder, int slot) {
        if (slot == BACK_SLOT) {
            openMain(player);
            return;
        }
        String subName = valueAt(player.getOpenInventory().getTopInventory(), slot);
        if (subName == null) {
            return;
        }
        SubCommand sub = subCommand(holder.module, subName);
        if (sub == null) {
            Text.msg(player, "&cThat command is no longer available.");
            openModule(player, holder.module);
            return;
        }
        ActionSpec spec = specFor(holder.module, sub.name());
        openAction(player, holder.module, sub, spec, new String[spec.params().size()]);
    }

    private void clickAction(Player player, Holder holder, int slot, boolean rightClick, String control) {
        if (slot == BACK_SLOT) {
            openModule(player, holder.module);
            return;
        }
        if (RUN_CONTROL.equals(control) || slot == RUN_SLOT) {
            run(player, holder);
            return;
        }
        int paramIndex = paramIndex(slot);
        if (paramIndex < 0 || holder.spec == null || paramIndex >= holder.spec.params().size()) {
            return;
        }
        if (rightClick) {
            holder.values[paramIndex] = null;
            openAction(player, holder.module, subCommand(holder.module, holder.sub), holder.spec, holder.values);
            return;
        }
        if (choices(player, subCommand(holder.module, holder.sub), holder.spec, holder.values, paramIndex).isEmpty()) {
            prompt(player, holder, paramIndex);
        } else {
            openChoices(player, holder, paramIndex);
        }
    }

    private void clickChoices(Player player, Holder holder, int slot) {
        if (slot == BACK_SLOT) {
            openAction(player, holder.module, subCommand(holder.module, holder.sub), holder.spec, holder.values);
            return;
        }
        if (slot == TYPE_SLOT) {
            prompt(player, holder, holder.paramIndex);
            return;
        }
        if (slot == CLEAR_SLOT) {
            holder.values[holder.paramIndex] = null;
            openAction(player, holder.module, subCommand(holder.module, holder.sub), holder.spec, holder.values);
            return;
        }
        String choice = valueAt(player.getOpenInventory().getTopInventory(), slot);
        if (choice == null) {
            return;
        }
        holder.values[holder.paramIndex] = choice;
        openAction(player, holder.module, subCommand(holder.module, holder.sub), holder.spec, holder.values);
    }

    private void run(Player player, Holder holder) {
        SubCommand sub = subCommand(holder.module, holder.sub);
        if (sub == null || holder.spec == null) {
            Text.msg(player, "&cThat command is no longer available.");
            openMain(player);
            return;
        }
        List<String> missing = missingRequired(holder.module, holder.sub, holder.spec, holder.values);
        if (!missing.isEmpty()) {
            Text.msg(player, "&cMissing parameter: &f" + String.join(", ", missing));
            openAction(player, holder.module, sub, holder.spec, holder.values);
            return;
        }
        String[] args = buildArgs(player, holder.module, holder.sub, holder.spec, holder.values);
        String module = holder.module;
        String subName = holder.sub;
        runAfterClick(player, () -> root.executeFromMenu(player, module, subName, args));
    }

    private void runAfterClick(Player player, Runnable action) {
        UUID playerId = player.getUniqueId();
        if (!pendingRuns.add(playerId)) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                if (!player.isOnline()) {
                    return;
                }
                player.closeInventory();
                action.run();
            } finally {
                pendingRuns.remove(playerId);
            }
        });
    }

    private void prompt(Player player, Holder holder, int paramIndex) {
        if (holder.spec == null || paramIndex < 0 || paramIndex >= holder.spec.params().size()) {
            return;
        }
        ParamSpec param = holder.spec.params().get(paramIndex);
        prompts.put(player.getUniqueId(), new PendingPrompt(holder.module, holder.sub, holder.spec,
                holder.values.clone(), paramIndex));
        player.closeInventory();
        Text.msg(player, "&aType value for &f" + param.name() + "&a in chat. Type &fcancel&a to return.");
        Text.msg(player, "&7" + param.description());
    }

    private SubCommand subCommand(String module, String subName) {
        return root.subCommand(module, subName);
    }

    private List<String> choices(Player player, SubCommand sub, ActionSpec spec, String[] values, int paramIndex) {
        if (sub == null || spec == null || paramIndex < 0 || paramIndex >= spec.params().size()) {
            return List.of();
        }
        ParamSpec param = spec.params().get(paramIndex);
        if (!param.choices().isEmpty()) {
            return param.choices();
        }
        String[] partial = partialArgs(spec, values, paramIndex);
        List<String> completed = sub.tabComplete(player, partial);
        if (completed == null || completed.isEmpty()) {
            return List.of();
        }
        return completed.stream().filter(value -> value != null && !value.isBlank()).limit(45).toList();
    }

    private static String[] partialArgs(ActionSpec spec, String[] values, int paramIndex) {
        List<String> args = new ArrayList<>();
        for (int i = 0; i <= paramIndex && i < spec.params().size(); i++) {
            String value = i < values.length ? values[i] : null;
            args.add(value == null ? "" : value);
        }
        return args.toArray(new String[0]);
    }

    private static String[] buildArgs(Player player, String module, String sub, ActionSpec spec, String[] values) {
        values = normalizedValues(player, module, sub, values);
        List<String> args = new ArrayList<>();
        for (int i = 0; i < spec.params().size(); i++) {
            String value = i < values.length ? values[i] : null;
            if (value != null && !value.isBlank()) {
                args.add(value.trim());
            }
        }
        return args.toArray(new String[0]);
    }

    private static String[] normalizedValues(Player player, String module, String sub, String[] values) {
        String[] out = values == null ? new String[0] : values.clone();
        if (!"place".equals(sub) || !"moto".equals(module) || out.length <= 4 || player == null) {
            return out;
        }
        boolean laterValue = false;
        for (int i = 4; i < out.length; i++) {
            if (out[i] != null && !out[i].isBlank()) {
                laterValue = true;
                break;
            }
        }
        if (laterValue && (out[3] == null || out[3].isBlank())) {
            out[3] = player.getWorld().getName();
        }
        return out;
    }

    private static List<String> missingRequired(String module, String sub, ActionSpec spec, String[] values) {
        List<String> missing = new ArrayList<>();
        for (int i = 0; i < spec.params().size(); i++) {
            ParamSpec param = spec.params().get(i);
            if (param.required() && blank(values, i)) {
                missing.add(param.name());
            }
        }
        return missing;
    }

    private static boolean blank(String[] values, int index) {
        return index >= values.length || values[index] == null || values[index].isBlank();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static int paramIndex(int slot) {
        for (int i = 0; i < PARAM_SLOTS.length; i++) {
            if (PARAM_SLOTS[i] == slot) {
                return i;
            }
        }
        return -1;
    }

    private static String valueAt(Inventory inventory, int slot) {
        if (slot < 0 || slot >= inventory.getSize()) {
            return null;
        }
        return valueOf(inventory.getItem(slot));
    }

    private static String valueOf(ItemStack item) {
        if (item == null || item.getType().isAir() || item.getItemMeta() == null) {
            return null;
        }
        return item.getItemMeta().getPersistentDataContainer()
                .get(me.bibo.militarycraft.core.key.Keys.of("menu", "value"),
                        org.bukkit.persistence.PersistentDataType.STRING);
    }

    private static ItemStack actionButton(String action) {
        Material material = switch (action) {
            case "reload" -> Material.REDSTONE;
            case "cleanup" -> Material.BUCKET;
            default -> Material.COMPARATOR;
        };
        return valueButton(material, display(action), NamedTextColor.GOLD,
                List.of("Run /mc " + action + "."), action);
    }

    private static ItemStack moduleButton(String module) {
        return valueButton(moduleMaterial(module), display(module), NamedTextColor.AQUA,
                List.of("Open " + display(module) + " commands.",
                        "Only actions you can use are shown."), module);
    }

    private static ItemStack subButton(String module, SubCommand sub) {
        ActionSpec spec = specFor(module, sub.name());
        return valueButton(spec.icon(), display(sub.name()), NamedTextColor.GREEN,
                List.of(spec.description(), "Permission: " + nullToPublic(sub.permission())), sub.name());
    }

    private static ItemStack parameterButton(ParamSpec param, String value, List<String> choices) {
        List<String> lore = new ArrayList<>();
        lore.add(param.description());
        lore.add("Current: " + (value == null || value.isBlank() ? "<empty>" : value));
        lore.add(choices.isEmpty() ? "Click to type in chat." : "Click to choose. Shift/right-click clears.");
        if (param.required()) {
            lore.add("Required.");
        }
        return button(param.material(), param.name(), param.required() ? NamedTextColor.YELLOW : NamedTextColor.WHITE, lore);
    }

    private static List<String> runLore(Player player, String module, String sub, ActionSpec spec, String[] values) {
        String joined = String.join(" ", buildArgs(player, module, sub, spec, values));
        return List.of("Execute this command.",
                "/mc " + module + " " + sub + (joined.isBlank() ? "" : " " + joined));
    }

    private static ItemStack valueButton(Material material, String name, NamedTextColor color, List<String> lore, String value) {
        ItemStack item = button(material, name, color, lore);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(me.bibo.militarycraft.core.key.Keys.of("menu", "value"),
                    org.bukkit.persistence.PersistentDataType.STRING, value);
            item.setItemMeta(meta);
        }
        return item;
    }

    private static ItemStack button(Material material, String name, NamedTextColor color, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        meta.displayName(Component.text(name, color).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore.stream()
                .map(line -> Component.text(line, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false))
                .toList());
        item.setItemMeta(meta);
        return item;
    }

    private static String nullToPublic(String permission) {
        return permission == null || permission.isBlank() ? "none" : permission;
    }

    private static String display(String id) {
        String[] parts = id.replace('_', ' ').replace('-', ' ').split(" ");
        return Arrays.stream(parts)
                .filter(part -> !part.isBlank())
                .map(part -> part.substring(0, 1).toUpperCase(Locale.ROOT) + part.substring(1))
                .reduce((a, b) -> a + " " + b)
                .orElse(id);
    }

    private static Material moduleMaterial(String module) {
        return switch (module) {
            case "tank" -> Material.GREEN_CONCRETE;
            case "kamaz" -> Material.MOSS_BLOCK;
            case "moto" -> Material.SADDLE;
            case "pickup" -> Material.CROSSBOW;
            case "jet" -> Material.ELYTRA;
            case "helicopter" -> Material.PHANTOM_MEMBRANE;
            case "airship" -> Material.WHITE_WOOL;
            case "drone" -> Material.FIREWORK_ROCKET;
            case "train" -> Material.RAIL;
            case "antiair" -> Material.DISPENSER;
            case "tckbus" -> Material.ZOMBIE_HEAD;
            case "airstrike" -> Material.BLAZE_POWDER;
            case "nuke" -> Material.TNT;
            case "warkit" -> Material.IRON_CHESTPLATE;
            case "artillery" -> Material.TARGET;
            default -> Material.CHEST;
        };
    }

    private static ActionSpec specFor(String module, String sub) {
        Material icon = actionMaterial(module, sub);
        List<ParamSpec> params = new ArrayList<>();
        switch (sub) {
            case "give" -> {
                if ("warkit".equals(module)) {
                    params.add(ParamSpec.required("item", Material.CHEST, "WarKit item id."));
                    params.add(ParamSpec.optional("player", Material.PLAYER_HEAD, "Target player. Empty means yourself."));
                    params.add(ParamSpec.optional("amount", Material.HOPPER, "Item amount. Empty means one."));
                } else if ("airstrike".equals(module) || "nuke".equals(module)) {
                    // These original commands only give their item to the command sender.
                } else {
                    if ("tckbus".equals(module)) {
                        params.add(ParamSpec.optional("player/skin", Material.PLAYER_HEAD,
                                "Target player, or a bus skin for yourself. Empty means yourself."));
                        params.add(ParamSpec.optionalChoices("skin", Material.PAINTING, "Bus skin.", List.of("tck", "tzahal")));
                    } else if ("moto".equals(module)) {
                        params.add(ParamSpec.optional("player/variant", Material.PLAYER_HEAD,
                                "Target player, or solo for yourself. Empty means yourself."));
                        params.add(ParamSpec.optionalChoices("variant", Material.SADDLE, "Use solo for no sidecar.", List.of("solo")));
                    } else {
                        params.add(ParamSpec.optional("player", Material.PLAYER_HEAD, "Target player. Empty means yourself."));
                    }
                }
            }
            case "giveall" -> params.add(ParamSpec.optional("player", Material.PLAYER_HEAD, "Target player. Empty means yourself."));
            case "spawn" -> {
                if ("moto".equals(module)) {
                    params.add(ParamSpec.optionalChoices("variant", Material.SADDLE, "Use solo for no sidecar.", List.of("solo")));
                } else if ("tckbus".equals(module)) {
                    params.add(ParamSpec.optionalChoices("skin", Material.PAINTING, "Bus skin.", List.of("tck", "tzahal")));
                }
            }
            case "place" -> {
                if ("antiair".equals(module)) {
                    // The original Anti-Air command places the turret in front of the player.
                } else if ("train".equals(module)) {
                    params.add(ParamSpec.optional("world", Material.GRASS_BLOCK, "World name."));
                    params.add(ParamSpec.required("x", Material.COMPASS, "Rail X coordinate."));
                    params.add(ParamSpec.required("y", Material.SCAFFOLDING, "Rail Y coordinate."));
                    params.add(ParamSpec.required("z", Material.COMPASS, "Rail Z coordinate."));
                } else {
                    params.add(ParamSpec.required("x", Material.COMPASS, "X coordinate."));
                    params.add(ParamSpec.required("y", Material.SCAFFOLDING, "Y coordinate."));
                    params.add(ParamSpec.required("z", Material.COMPASS, "Z coordinate."));
                    if (!"pickup".equals(module)) {
                        params.add(ParamSpec.optional("world", Material.GRASS_BLOCK, "World name."));
                    }
                }
                if ("moto".equals(module)) {
                    params.add(ParamSpec.optionalChoices("yaw", Material.COMPASS, "Yaw angle.", List.of("0", "90", "180", "-90")));
                    params.add(ParamSpec.optionalChoices("variant", Material.SADDLE, "Use solo for no sidecar.", List.of("solo")));
                } else if ("tckbus".equals(module)) {
                    params.add(ParamSpec.optionalChoices("skin", Material.PAINTING, "Bus skin.", List.of("tck", "tzahal")));
                }
            }
            case "call" -> {
                // Airstrike call uses the player's crosshair, matching the original command.
            }
            case "fire" -> {
                if ("artillery".equals(module)) {
                    params.add(ParamSpec.required("x", Material.TARGET, "Target X coordinate."));
                    params.add(ParamSpec.required("z", Material.TARGET, "Target Z coordinate."));
                }
            }
            case "rotate" -> params.add(ParamSpec.optional("yaw", Material.COMPASS, "Yaw angle. Empty uses your current yaw."));
            case "mode" -> params.add(ParamSpec.optionalChoices("mode", Material.COMPARATOR,
                    "Anti-Air targeting mode. Empty cycles to next mode.", List.of("phantoms", "hostiles", "svo")));
            case "skin" -> params.add(ParamSpec.requiredChoices("skin", Material.PAINTING,
                    "TCK Bus skin.", List.of("tck", "tzahal")));
            default -> {
            }
        }
        return new ActionSpec(icon, descriptionFor(module, sub), List.copyOf(params));
    }

    private static Material actionMaterial(String module, String sub) {
        if ("drone".equals(module) && "fire".equals(sub)) {
            return Material.FIREWORK_ROCKET;
        }
        return switch (sub) {
            case "give", "giveall" -> Material.CHEST;
            case "spawn", "create" -> Material.EMERALD_BLOCK;
            case "place", "call", "fire" -> Material.TARGET;
            case "remove", "cleanup", "cleardrop" -> Material.TNT;
            case "list", "showdrop" -> Material.BOOK;
            case "mode", "skin", "target", "rotate" -> Material.COMPARATOR;
            case "enter" -> Material.ENDER_PEARL;
            case "exit" -> Material.OAK_DOOR;
            case "setdrop" -> Material.HOPPER;
            default -> Material.PAPER;
        };
    }

    private static String descriptionFor(String module, String sub) {
        if ("drone".equals(module) && "fire".equals(sub)) {
            return "Fire the piloted drone rocket.";
        }
        return switch (sub) {
            case "give" -> "Give a placer or item to a player.";
            case "giveall" -> "Give every item from this module.";
            case "spawn", "create" -> "Create one near you or where you look.";
            case "place" -> "antiair".equals(module)
                    ? "Place one in front of you."
                    : "Create one at exact coordinates.";
            case "remove" -> "Remove the nearest/looked-at object.";
            case "list" -> "Print active objects to chat.";
            case "cleanup" -> "Clean stale or active objects for this module.";
            case "call" -> "Call support at the target you are looking at.";
            case "fire" -> "Fire at exact X/Z coordinates.";
            case "enter" -> "Enter or operate the nearest object.";
            case "exit" -> "Leave the active operation session.";
            case "rotate" -> "Rotate the selected/nearest object.";
            case "mode" -> "Change Anti-Air targeting mode.";
            case "skin" -> "Change TCK Bus skin.";
            case "setdrop" -> "Save TCK Bus custom drops from hotbar.";
            case "cleardrop" -> "Clear TCK Bus custom drops.";
            case "showdrop" -> "Show TCK Bus custom drops.";
            default -> "Run this subcommand.";
        };
    }

    private enum View {
        MAIN,
        MODULE,
        ACTION,
        CHOICES
    }

    private record ActionSpec(Material icon, String description, List<ParamSpec> params) {
    }

    private record ParamSpec(String name, Material material, String description, boolean required, List<String> choices) {
        static ParamSpec required(String name, Material material, String description) {
            return text(name, material, description, true);
        }

        static ParamSpec optional(String name, Material material, String description) {
            return text(name, material, description, false);
        }

        static ParamSpec text(String name, Material material, String description, boolean required) {
            return new ParamSpec(name, material, description, required, List.of());
        }

        static ParamSpec requiredChoices(String name, Material material, String description, List<String> choices) {
            return new ParamSpec(name, material, description, true, choices);
        }

        static ParamSpec optionalChoices(String name, Material material, String description, List<String> choices) {
            return new ParamSpec(name, material, description, false, choices);
        }
    }

    private static final class Holder implements InventoryHolder {
        private final View view;
        private final String module;
        private final String sub;
        private final ActionSpec spec;
        private final String[] values;
        private final int paramIndex;
        private Inventory inventory;

        private Holder(View view, String module, String sub, ActionSpec spec, String[] values, int paramIndex) {
            this.view = view;
            this.module = module;
            this.sub = sub;
            this.spec = spec;
            this.values = values == null ? new String[0] : values.clone();
            this.paramIndex = paramIndex;
        }

        static Holder main() {
            return new Holder(View.MAIN, null, null, null, null, -1);
        }

        static Holder module(String module) {
            return new Holder(View.MODULE, module, null, null, null, -1);
        }

        static Holder action(String module, String sub, ActionSpec spec, String[] values) {
            return new Holder(View.ACTION, module, sub, spec, values, -1);
        }

        static Holder choices(String module, String sub, ActionSpec spec, String[] values, int paramIndex) {
            return new Holder(View.CHOICES, module, sub, spec, values, paramIndex);
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private record PendingPrompt(String module, String sub, ActionSpec spec, String[] values, int paramIndex) {
    }
}
