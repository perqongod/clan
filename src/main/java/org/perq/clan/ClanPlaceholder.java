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
            return "";
        }

        switch (params.toLowerCase()) {
            case "tag":
                // %clan_tag% - Clan-Tag des Spielers (mit Farbcodes für VIP)
                return plugin.getConfigManager().translateColors(playerData.getClanTag());
            
            case "role":
                // %clan_role% - Rolle des Spielers (MEMBER, MOD, LEADER)
                return playerData.getRole();
            
            case "info":
                // %clan_info% - Clan-Tag + Rolle
                return playerData.getClanTag() + " " + playerData.getRole();
            
            case "suffix":
                // %clan_suffix% - Nur Clan-Tag mit Farben
                return plugin.getConfigManager().translateColors(
                    "&8[&b" + playerData.getClanTag() + "&8]"
                );
            
            case "points":
                // %clan_points% - Punkte des Clans
                ClanData clan = plugin.getFileManager().loadClan(playerData.getClanTag());
                if (clan != null) {
                    return String.valueOf(clan.getPoints());
                }
                return "0";
            
            case "rank":
                // %clan_rank% - Rang des Clans
                ClanData clanRank = plugin.getFileManager().loadClan(playerData.getClanTag());
                if (clanRank != null) {
                    return clanRank.getRank();
                }
                return "Unbekannt";
            
            case "members":
                // %clan_members% - Anzahl der Mitglieder
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
