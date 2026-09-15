package com.h2ph.teams.echest;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.locks.ReentrantLock;

public class TeamEnderChestHolder implements InventoryHolder {

    private final String teamId;
    private final String teamName;
    private final ReentrantLock lock = new ReentrantLock();
    private Inventory inventory;

    public TeamEnderChestHolder(String teamId, String teamName) {
        this.teamId = teamId;
        this.teamName = teamName;
    }

    public String getTeamId() {
        return teamId;
    }

    public String getTeamName() {
        return teamName;
    }

    public ReentrantLock getLock() {
        return lock;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
