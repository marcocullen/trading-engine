package com.trading.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * MACD-specific result with all components
 */
public record MACDResult(
        LocalDate date,
        BigDecimal macdLine,
        BigDecimal signalLine,
        BigDecimal histogram
) {
    /**
     * Convert to JSON metadata string
     */
    public String toMetadata() {
        return String.format(
                "{\"macdLine\":%.6f,\"signalLine\":%.6f,\"histogram\":%.6f}",
                macdLine, signalLine, histogram
        );
    }

    /**
     * Check if this is a bullish crossover (buy signal)
     */
    public boolean isBullishCrossover(MACDResult previous) {
        if (previous == null) return false;
        return previous.histogram.compareTo(BigDecimal.ZERO) < 0
                && histogram.compareTo(BigDecimal.ZERO) >= 0;
    }

    /**
     * Check if this is a bearish crossover (sell signal)
     */
    public boolean isBearishCrossover(MACDResult previous) {
        if (previous == null) return false;
        return previous.histogram.compareTo(BigDecimal.ZERO) > 0
                && histogram.compareTo(BigDecimal.ZERO) <= 0;
    }
}
