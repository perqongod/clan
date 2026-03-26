package org.perq.clan;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;

public class ClanChatCommand implements CommandExecutor {
    private final Clan plugin;

    public ClanChatCommand(Clan plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        ConfigManager configManager = plugin.getConfigManager();
        if (!(sender instanceof Player)) {
            sender.sendMessage(configManager.formatPlain("This command can only be used by players."));
            return true;
        }

        Player player = (Player) sender;
        ClanData clan = getPlayerClan(player.getUniqueId());
        if (clan == null) {
            player.sendMessage(plugin.getConfigManager().getMessage("no-clan"));
            return true;
        }

        if (args.length == 0) {
            player.sendMessage(configManager.formatPlain(configManager.getPrefix() + "Usage: /c <message>"));
            return true;
        }

        String message = String.join(" ", args);
        String format = configManager.getClanChatFormat()
                .replace("%player%", configManager.formatPlain(player.getName()))
                .replace("%message%", configManager.formatPlain(message));

        for (UUID mem : clan.getMembers()) {
            Player p = plugin.getServer().getPlayer(mem);
            if (p != null) {
                p.sendMessage(format);
            }
        }

        return true;
    }

    private ClanData getPlayerClan(UUID player) {
        PlayerData p = plugin.getFileManager().loadPlayer(player);
        if (p == null || p.getClanTag() == null) return null;
        return plugin.getFileManager().loadClan(p.getClanTag());
    }
}
