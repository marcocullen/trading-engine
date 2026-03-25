package com.trading.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Represents a historical trading signal with its actual outcome
 * Used for backtesting and statistical validation
 */
public record HistoricalSignal(
        String symbol,
        LocalDate signalDate,
        TradingSignal signal,
        Price entryPrice,
        Price exitPrice,
        int holdingPeriodDays,
        BigDecimal returnPercent,
        BigDecimal volatility,
        boolean isWinner
) {
    /**
     * Create from signal and outcome data
     */
    public static HistoricalSignal of(
            TradingSignal signal,
            Price entryPrice,
            Price exitPrice,
            int holdingPeriodDays) {
        
        BigDecimal returnPct = exitPrice.value()
                .subtract(entryPrice.value())
                .divide(entryPrice.value(), 6, BigDecimal.ROUND_HALF_UP)
                .multiply(BigDecimal.valueOf(100));
        
        boolean isWinner = returnPct.compareTo(BigDecimal.ZERO) > 0;
        
        return new HistoricalSignal(
                signal.symbol(),
                signal.date(),
                signal,
                entryPrice,
                exitPrice,
                holdingPeriodDays,
                returnPct,
                null, // Volatility calculated separately
                isWinner
        );
    }
    
    /**
     * With volatility included
     */
    public HistoricalSignal withVolatility(BigDecimal volatility) {
        return new HistoricalSignal(
                symbol,
                signalDate,
                signal,
                entryPrice,
                exitPrice,
                holdingPeriodDays,
                returnPercent,
                volatility,
                isWinner
        );
    }
    
    /**
     * Calculate Sharpe ratio (simplified, assuming 0% risk-free rate)
     */
    public BigDecimal sharpeRatio() {
        if (volatility == null || volatility.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return returnPercent.divide(volatility, 4, BigDecimal.ROUND_HALF_UP);
    }
    
    /**
     * Get signal score
     */
    public int score() {
        return signal.score();
    }
    
    /**
     * Get individual component scores
     */
    public SignalComponents components() {
        return signal.components();
    }
}