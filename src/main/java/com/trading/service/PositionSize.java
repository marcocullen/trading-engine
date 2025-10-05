package com.trading.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Calculated position size with risk metrics
 */
public record PositionSize(
        String symbol,
        BigDecimal shares,
        BigDecimal entryPrice,
        BigDecimal investmentAmount,
        BigDecimal stopLossPrice,
        BigDecimal riskAmount,
        BigDecimal portfolioPercent
) {
    /**
     * Print formatted position details
     */
    public void print() {
        System.out.printf("\n📊 Position Size: %s%n", symbol);
        System.out.printf("   Shares:     %.0f%n", shares);
        System.out.printf("   Entry:      £%.2f%n", entryPrice);
        System.out.printf("   Investment: £%.2f (%.2f%% of portfolio)%n",
                investmentAmount, portfolioPercent);
        System.out.printf("   Stop Loss:  £%.2f (%.2f%% below entry)%n",
                stopLossPrice,
                entryPrice.subtract(stopLossPrice)
                        .divide(entryPrice, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100)));
        System.out.printf("   Risk:       £%.2f%n", riskAmount);
    }

    /**
     * Check if position is valid (non-zero shares)
     */
    public boolean isValid() {
        return shares.compareTo(BigDecimal.ZERO) > 0;
    }
}
