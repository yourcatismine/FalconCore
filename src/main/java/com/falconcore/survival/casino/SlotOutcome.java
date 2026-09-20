package com.falconcore.survival.casino;

public class SlotOutcome {

    private final SlotSymbol reel1;
    private final SlotSymbol reel2;
    private final SlotSymbol reel3;
    private final double betAmount;
    private final double multiplier;
    private final double winAmount;
    private final boolean win;
    private final boolean jackpot;

    public SlotOutcome(SlotSymbol reel1, SlotSymbol reel2, SlotSymbol reel3, double betAmount) {
        this.reel1 = reel1;
        this.reel2 = reel2;
        this.reel3 = reel3;
        this.betAmount = betAmount;

        double calculatedMultiplier = 0.0;
        boolean isJackpot = false;

        if (reel1.getId().equals(reel2.getId()) && reel2.getId().equals(reel3.getId())) {
            // 3 matching symbols
            calculatedMultiplier = reel1.getMultiplier3x();
            // Top jackpot if weight is small or highest multiplier
            if (calculatedMultiplier >= 50.0 || reel1.getWeight() <= 10) {
                isJackpot = true;
            }
        } else if (reel1.getId().equals(reel2.getId())) {
            // 2 matching symbols (Reel 1 & Reel 2)
            calculatedMultiplier = reel1.getMultiplier2x();
        } else if (reel2.getId().equals(reel3.getId())) {
            // 2 matching symbols (Reel 2 & Reel 3)
            calculatedMultiplier = reel2.getMultiplier2x();
        } else if (reel1.getId().equals(reel3.getId())) {
            // 2 matching symbols (Reel 1 & Reel 3)
            calculatedMultiplier = reel1.getMultiplier2x();
        }

        this.multiplier = calculatedMultiplier;
        this.winAmount = betAmount * calculatedMultiplier;
        this.win = this.winAmount > 0;
        this.jackpot = isJackpot && this.win;
    }

    public SlotSymbol getReel1() {
        return reel1;
    }

    public SlotSymbol getReel2() {
        return reel2;
    }

    public SlotSymbol getReel3() {
        return reel3;
    }

    public double getBetAmount() {
        return betAmount;
    }

    public double getMultiplier() {
        return multiplier;
    }

    public double getWinAmount() {
        return winAmount;
    }

    public boolean isWin() {
        return win;
    }

    public boolean isJackpot() {
        return jackpot;
    }

    public SlotSymbol getSymbolAtReel(int reelIndex) {
        switch (reelIndex) {
            case 1:
                return reel1;
            case 2:
                return reel2;
            case 3:
                return reel3;
            default:
                return reel1;
        }
    }
}
