package com.falconcore.survival.casino;

import com.falconcore.survival.casino.config.CasinoConfig;
import com.falconcore.survival.casino.gui.CasinoSlotGUI;
import com.falconcore.survival.manager.PlayerData;
import com.h2ph.Falcon;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.InventoryView;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class CasinoManager {

    private final Falcon plugin;
    private final CasinoConfig config;
    private final Map<UUID, ActiveSpin> activeSpins = new ConcurrentHashMap<>();

    public static class ActiveSpin {
        private final UUID playerUUID;
        private final double betAmount;
        private final double taxAmount;
        private final double totalDeducted;
        private final SlotOutcome outcome;
        private BukkitTask task;

        public ActiveSpin(UUID playerUUID, double betAmount, double taxAmount, double totalDeducted, SlotOutcome outcome) {
            this.playerUUID = playerUUID;
            this.betAmount = betAmount;
            this.taxAmount = taxAmount;
            this.totalDeducted = totalDeducted;
            this.outcome = outcome;
        }

        public UUID getPlayerUUID() {
            return playerUUID;
        }

        public double getBetAmount() {
            return betAmount;
        }

        public double getTaxAmount() {
            return taxAmount;
        }

        public double getTotalDeducted() {
            return totalDeducted;
        }

        public SlotOutcome getOutcome() {
            return outcome;
        }

        public BukkitTask getTask() {
            return task;
        }

        public void setTask(BukkitTask task) {
            this.task = task;
        }
    }

    public CasinoManager(Falcon plugin) {
        this.plugin = plugin;
        this.config = new CasinoConfig(plugin);
    }

    public CasinoConfig getConfig() {
        return config;
    }

    public void reload() {
        config.loadConfig();
    }

    public boolean isSpinning(UUID playerUUID) {
        return activeSpins.containsKey(playerUUID);
    }

    public void openCasino(Player player) {
        openCasino(player, config.getDefaultBet());
    }

    public void openCasino(Player player, double initialBet) {
        if (!config.isEnabled()) {
            player.sendMessage(ChatColor.RED + "The Casino is currently disabled.");
            return;
        }
        CasinoSlotGUI gui = new CasinoSlotGUI(plugin, player, initialBet);
        if (isSpinning(player.getUniqueId())) {
            gui.setSpinning(true);
        }
        player.openInventory(gui.getInventory());
        config.getClickSound().play(player);
    }

    /**
     * Executes the slot machine spin with full anti-exploit guarantees.
     * Deducts bet + tax atomically, resolves outcome, and animates reels.
     */
    public boolean processSpin(Player player, CasinoSlotGUI gui) {
        if (!config.isEnabled()) {
            player.sendMessage(ChatColor.RED + "The Casino is currently disabled.");
            return false;
        }

        UUID uuid = player.getUniqueId();

        // 1. Anti-Exploit: Check if already spinning
        if (activeSpins.containsKey(uuid)) {
            String msg = config.getFormattedMessage("already-spinning", "&cYou already have a spin in progress!");
            player.sendMessage(msg);
            config.getLoseSound().play(player);
            return false;
        }

        double bet = gui.getCurrentBet();
        double minBet = config.getMinBet();
        double maxBet = config.getMaxBet();

        if (bet < minBet) {
            String msg = config.getFormattedMessage("min-bet", "&cMinimum bet is &e$" + minBet)
                    .replace("%min%", String.format("%.2f", minBet));
            player.sendMessage(msg);
            config.getLoseSound().play(player);
            return false;
        }

        if (bet > maxBet) {
            String msg = config.getFormattedMessage("max-bet", "&cMaximum bet is &e$" + maxBet)
                    .replace("%max%", String.format("%.2f", maxBet));
            player.sendMessage(msg);
            config.getLoseSound().play(player);
            return false;
        }

        double tax = config.calculateTax(bet);
        double totalCost = bet + tax;

        PlayerData data = plugin.getPlayerDataManager().get(uuid);
        if (data == null || data.getMoney() < totalCost) {
            double currentBal = data != null ? data.getMoney() : 0.0;
            String msg = config.getFormattedMessage("insufficient-funds", "&cInsufficient funds! Total: &e$%total%&c, Balance: &e$%balance%")
                    .replace("%total%", String.format("%.2f", totalCost))
                    .replace("%tax%", String.format("%.1f", config.getTaxPercentage()))
                    .replace("%balance%", String.format("%.2f", currentBal));
            player.sendMessage(msg);
            config.getLoseSound().play(player);
            return false;
        }

        // 2. Anti-Exploit: Atomic deduction of (Bet + Tax) before ANY animation
        data.removeMoney(totalCost, "Casino Slot Machine Bet ($" + String.format("%.2f", bet) + " + $" + String.format("%.2f", tax) + " tax)");
        plugin.getPlayerDataManager().savePlayerAsync(uuid);

        // 3. Pre-generate Outcome
        SlotSymbol r1 = config.getRandomSymbol();
        SlotSymbol r2 = config.getRandomSymbol();
        SlotSymbol r3 = config.getRandomSymbol();
        SlotOutcome outcome = new SlotOutcome(r1, r2, r3, bet);

        ActiveSpin spin = new ActiveSpin(uuid, bet, tax, totalCost, outcome);
        activeSpins.put(uuid, spin);

        gui.setSpinning(true);
        config.getClickSound().play(player);

        String spinMsg = config.getFormattedMessage("spinning", "&eSpinning... Good luck!");
        player.sendMessage(spinMsg);

        // 4. Start Multi-stage Animation Task
        startAnimation(player, gui, spin);
        return true;
    }

    private void startAnimation(Player player, CasinoSlotGUI gui, ActiveSpin spin) {
        int totalSteps = config.getAnimTotalSteps();
        int stepReel1 = config.getAnimReel1StopStep();
        int stepReel2 = config.getAnimReel2StopStep();
        int stepReel3 = config.getAnimReel3StopStep();
        int tickInterval = config.getAnimTickInterval();

        final int[] currentStep = {0};

        BukkitTask task = plugin.getSchedulerAdapter().runTaskTimer(() -> {
            currentStep[0]++;
            int step = currentStep[0];
            UUID uuid = spin.getPlayerUUID();
            Player p = Bukkit.getPlayer(uuid);
            boolean isOnline = p != null && p.isOnline();

            // Check if GUI is still open
            boolean guiOpen = false;
            CasinoSlotGUI currentGui = null;
            if (isOnline) {
                InventoryView openInv = p.getOpenInventory();
                if (openInv != null && openInv.getTopInventory() != null && openInv.getTopInventory().getHolder() instanceof CasinoSlotGUI) {
                    currentGui = (CasinoSlotGUI) openInv.getTopInventory().getHolder();
                    guiOpen = true;
                }
            }

            // Animate Reel 1
            if (step < stepReel1) {
                SlotSymbol sym1 = config.getRandomSymbol();
                if (guiOpen && currentGui != null) {
                    currentGui.setReelSymbol(0, sym1);
                }
            } else if (step == stepReel1) {
                if (guiOpen && currentGui != null) {
                    currentGui.setReelSymbol(0, spin.getOutcome().getReel1());
                }
                if (isOnline) {
                    config.getReelLockSound().play(p);
                }
            }

            // Animate Reel 2
            if (step < stepReel2) {
                SlotSymbol sym2 = config.getRandomSymbol();
                if (guiOpen && currentGui != null) {
                    currentGui.setReelSymbol(1, sym2);
                }
            } else if (step == stepReel2) {
                if (guiOpen && currentGui != null) {
                    currentGui.setReelSymbol(1, spin.getOutcome().getReel2());
                }
                if (isOnline) {
                    config.getReelLockSound().play(p);
                }
            }

            // Animate Reel 3
            if (step < stepReel3) {
                SlotSymbol sym3 = config.getRandomSymbol();
                if (guiOpen && currentGui != null) {
                    currentGui.setReelSymbol(2, sym3);
                }
            } else if (step == stepReel3) {
                if (guiOpen && currentGui != null) {
                    currentGui.setReelSymbol(2, spin.getOutcome().getReel3());
                }
                if (isOnline) {
                    config.getReelLockSound().play(p);
                }
            }

            // Sound on cycling step
            if (step < stepReel3 && step != stepReel1 && step != stepReel2 && isOnline) {
                config.getSpinTickSound().play(p);
            }

            // Final step reached
            if (step >= totalSteps) {
                finishSpin(spin, p, guiOpen ? currentGui : null);
            }
        }, tickInterval, tickInterval);

        spin.setTask(task);
    }

    private void finishSpin(ActiveSpin spin, Player player, CasinoSlotGUI gui) {
        // Cancel task
        if (spin.getTask() != null) {
            try {
                spin.getTask().cancel();
            } catch (Exception ignored) {}
        }

        UUID uuid = spin.getPlayerUUID();
        SlotOutcome outcome = spin.getOutcome();
        double winAmount = outcome.getWinAmount();
        boolean won = outcome.isWin();
        boolean jackpot = outcome.isJackpot();

        // 1. Anti-Exploit Safe Deposit: Always credit PlayerData even if offline / inventory closed
        if (won && winAmount > 0) {
            PlayerData data = plugin.getPlayerDataManager().get(uuid);
            if (data != null) {
                data.addMoney(winAmount, "Casino Slot Machine Win (" + String.format("%.1f", outcome.getMultiplier()) + "x)");
                plugin.getPlayerDataManager().savePlayerAsync(uuid);
            }
        }

        // 2. Remove from active spins registry
        activeSpins.remove(uuid);

        // 3. Inform player if online
        if (player != null && player.isOnline()) {
            if (gui != null) {
                gui.displayFinalResult(outcome);
                gui.setSpinning(false);
            }

            if (won) {
                if (jackpot) {
                    config.getJackpotSound().play(player);
                    String topSymName = outcome.getReel1().getDisplayName();
                    String msg = config.getFormattedMessage("jackpot", "&6&l★ JACKPOT! ★ &eWon &a$%amount% &7(&e%multiplier%x&7)!")
                            .replace("%amount%", String.format("%.2f", winAmount))
                            .replace("%multiplier%", String.format("%.1f", outcome.getMultiplier()))
                            .replace("%symbol%", topSymName);
                    player.sendMessage(msg);
                } else {
                    config.getWinSound().play(player);
                    String msg = config.getFormattedMessage("win", "&a&lWIN! &7You won &a$%amount% &7(&e%multiplier%x&7)!")
                            .replace("%amount%", String.format("%.2f", winAmount))
                            .replace("%multiplier%", String.format("%.1f", outcome.getMultiplier()));
                    player.sendMessage(msg);
                }
            } else {
                config.getLoseSound().play(player);
                String msg = config.getFormattedMessage("lose", "&cNo luck! &7Lost &c$%amount%&7.")
                        .replace("%amount%", String.format("%.2f", spin.getBetAmount()));
                player.sendMessage(msg);
            }
        } else {
            // Player was offline during spin completion: safe deposit was already performed above!
            plugin.getLogger().log(Level.INFO, "[Casino] Completed offline spin for UUID {0}. Outcome Win: {1} (${2})",
                    new Object[]{uuid, won, winAmount});
        }
    }

    public void shutdown() {
        // Safe shutdown: cancel active tasks and ensure winnings are deposited
        for (Map.Entry<UUID, ActiveSpin> entry : activeSpins.entrySet()) {
            ActiveSpin spin = entry.getValue();
            if (spin.getTask() != null) {
                try {
                    spin.getTask().cancel();
                } catch (Exception ignored) {}
            }
            if (spin.getOutcome().isWin() && spin.getOutcome().getWinAmount() > 0) {
                try {
                    PlayerData data = plugin.getPlayerDataManager().get(spin.getPlayerUUID());
                    if (data != null) {
                        data.addMoney(spin.getOutcome().getWinAmount(), "Casino Slot Win (Server Shutdown Resolution)");
                        plugin.getPlayerDataManager().savePlayerSync(spin.getPlayerUUID());
                    }
                } catch (Exception e) {
                    plugin.getLogger().log(Level.SEVERE, "Error depositing pending casino win on shutdown for " + spin.getPlayerUUID(), e);
                }
            }
        }
        activeSpins.clear();
    }
}
