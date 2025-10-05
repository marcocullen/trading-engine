package com.trading.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Result of an indicator calculation for a specific date
 */
public record IndicatorResult(
        String symbol,
        LocalDate date,
        String indicatorName,
        BigDecimal value,
        String metadata  // Optional JSON metadata for complex indicators
) {
    /**
     * Create a simple indicator result
     */
    public static IndicatorResult of(String symbol, LocalDate date, String indicatorName, BigDecimal value) {
        return new IndicatorResult(symbol, date, indicatorName, value, null);
    }

    /**
     * Create indicator result with metadata
     */
    public static IndicatorResult of(String symbol, LocalDate date, String indicatorName,
                                     BigDecimal value, String metadata) {
        return new IndicatorResult(symbol, date, indicatorName, value, metadata);
    }
}
