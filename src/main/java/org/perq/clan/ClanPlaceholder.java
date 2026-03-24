package org.perq.clan;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ClanPlaceholder extends PlaceholderExpansion {
    private final Clan plugin;

    public ClanPlaceholder(Clan plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "clan";
    }

    @Override
    public @NotNull String getAuthor() {
        return "perq";
    }

    @Override
    public @NotNull String getVersion() {
        return "1.0";
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onPlaceholderRequest(Player player, @NotNull String params) {
        if (player == null) return null;

        PlayerData playerData = plugin.getFileManager().loadPlayer(player.getUniqueId());
        
        if (playerData == null || playerData.getClanTag() == null) {
            switch (params.toLowerCase()) {
                case "tag":
                    return plugin.getConfigManager().translateColors("&cN/A");
                case "tag2":
                    return plugin.getConfigManager().translateColors("&7[&cN/A&7]");
                default:
                    return null;
            }
        }

        switch (params.toLowerCase()) {
            case "tag":
                // %clan_tag% - player clan tag (with color codes for VIP)
                return plugin.getConfigManager().translateColors(playerData.getClanTag());

            case "tag2":
                // %clan_tag2% - formatted clan tag with brackets: &7[<tag>&7]
                return plugin.getConfigManager().translateColors(
                    "&7[" + playerData.getClanTag() + "&7]"
                );
            
            case "role":
                // %clan_role% - player role (MEMBER, MOD, LEADER)
                return playerData.getRole();
            
            case "info":
                // %clan_info% - clan tag + role
                return playerData.getClanTag() + " " + playerData.getRole();
            
            case "suffix":
                // %clan_suffix% - clan tag with colors
                return plugin.getConfigManager().translateColors(
                    "&7[&b" + playerData.getClanTag() + "&7]"
                );
            
            case "points":
                // %clan_points% - clan points
                ClanData clan = plugin.getFileManager().loadClan(playerData.getClanTag());
                if (clan != null) {
                    return String.valueOf(clan.getPoints());
                }
                return "0";
            
            case "rank":
                // %clan_rank% - clan rank
                ClanData clanRank = plugin.getFileManager().loadClan(playerData.getClanTag());
                if (clanRank != null) {
                    return clanRank.getRank();
                }
                return "Unknown";
            
            case "members":
                // %clan_members% - member count
                ClanData clanMembers = plugin.getFileManager().loadClan(playerData.getClanTag());
                if (clanMembers != null) {
                    return String.valueOf(clanMembers.getMembers().size());
                }
                return "0";
            
            default:
                return null;
        }
    }
}
