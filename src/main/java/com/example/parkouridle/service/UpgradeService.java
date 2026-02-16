package com.example.parkouridle.service;

import com.example.parkouridle.math.BigNumber;
import com.example.parkouridle.model.PlayerProgress;

public final class UpgradeService {

    private static final BigNumber[] BASE_COSTS = {
        BigNumber.of(100),
        BigNumber.of(250),
        BigNumber.of(5_000),
        BigNumber.of(100_000),
        BigNumber.of(1_000_000),
        BigNumber.of(10_000_000)
    };

    public BigNumber costForOption(int option, int level) {
        int idx = Math.max(0, Math.min(BASE_COSTS.length - 1, option - 1));
        return BASE_COSTS[idx].multiply(BigNumber.of(1.5).pow(level));
    }

    public BigNumber purchaseVoidPointCost(int currentVp) {
        return BigNumber.ofLayer(11.0 + (currentVp * 0.9), 2);
    }

    public BigNumber computeIdlePointsPerTick(PlayerProgress progress) {
        BigNumber points = BigNumber.of(1);

        double baseFactor = 1.1 + (progress.option2 * 0.01);
        points = points.multiply(BigNumber.of(baseFactor).pow(progress.option1));

        BigNumber rebirthBoost = BigNumber.of(2).pow(progress.option3);
        BigNumber ascensionBoost = rebirthBoost.pow(1.0 + (0.1 * progress.option4));
        points = points.multiply(ascensionBoost);

        points = points.pow(1.0 + (0.05 * progress.option5));
        points = points.layerAdd(0.1 * progress.option6);

        return points;
    }
}
