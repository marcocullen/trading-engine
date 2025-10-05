package com.trading.domain;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Immutable market data for a single trading day
 * Using BigDecimal for price precision
 */
public record MarketData(
    String symbol,
    LocalDate date,
    Price open,
    Price high,
    Price low,
    Price close,
    Price adjustedClose,
    long volume
) {
    public MarketData {
        Objects.requireNonNull(symbol, "Symbol cannot be null");
        Objects.requireNonNull(date, "Date cannot be null");
        Objects.requireNonNull(open, "Open price cannot be null");
        Objects.requireNonNull(high, "High price cannot be null");
        Objects.requireNonNull(low, "Low price cannot be null");
        Objects.requireNonNull(close, "Close price cannot be null");
        Objects.requireNonNull(adjustedClose, "Adjusted close price cannot be null");
        
        if (volume < 0) {
            throw new IllegalArgumentException("Volume cannot be negative");
        }
    }
    
    /**
     * Check if this is a valid trading day (has volume)
     */
    public boolean isValidTradingDay() {
        return volume > 0;
    }
}