package me.bibo.militarycraft.core.command;

import org.bukkit.command.CommandSender;

import java.util.List;

/** One `/mc <module> <sub>` action, registered by a module via {@code core.commands()}. */
public interface SubCommand {

    String name();

    /** Permission node required, e.g. "militarycraft.tank.spawn"; null/blank = no extra check. */
    String permission();

    void execute(CommandSender sender, String[] args);

    default List<String> tabComplete(CommandSender sender, String[] args) {
        return List.of();
    }
}
