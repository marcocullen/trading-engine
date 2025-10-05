package com.trading.service;

import java.math.BigDecimal;

/**
 * Risk management parameters
 */
public record RiskParameters(
        BigDecimal maxPositionPercent,   // Max % of portfolio per position (e.g., 10%)
        BigDecimal riskPerTradePercent,  // Max % to risk per trade (e.g., 2%)
        BigDecimal stopLossPercent,      // Stop loss % below entry (e.g., 10%)
        BigDecimal minPositionValue      // Minimum position size in £ (e.g., 500)
) {
    /**
     * Conservative risk parameters for small accounts
     */
    public static RiskParameters conservative() {
        return new RiskParameters(
                BigDecimal.valueOf(5),    // 5% max per position
                BigDecimal.valueOf(1),    // Risk 1% per trade
                BigDecimal.valueOf(8),    // 8% stop loss
                BigDecimal.valueOf(500)   // Minimum £500 position
        );
    }

    /**
     * Moderate risk parameters
     */
    public static RiskParameters moderate() {
        return new RiskParameters(
                BigDecimal.valueOf(10),   // 10% max per position
                BigDecimal.valueOf(2),    // Risk 2% per trade
                BigDecimal.valueOf(10),   // 10% stop loss
                BigDecimal.valueOf(1000)  // Minimum £1000 position
        );
    }

    /**
     * Aggressive risk parameters
     */
    public static RiskParameters aggressive() {
        return new RiskParameters(
                BigDecimal.valueOf(15),   // 15% max per position
                BigDecimal.valueOf(3),    // Risk 3% per trade
                BigDecimal.valueOf(12),   // 12% stop loss
                BigDecimal.valueOf(1500)  // Minimum £1500 position
        );
    }
}
