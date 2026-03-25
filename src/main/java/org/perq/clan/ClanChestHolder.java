package org.perq.clan;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public class ClanChestHolder implements InventoryHolder {
    private final String clanTag;
    private Inventory inventory;

    public ClanChestHolder(String clanTag) {
        this.clanTag = clanTag;
    }

    public String getClanTag() {
        return clanTag;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
