package com.trading.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Represents a trading signal (buy/sell recommendation) for a symbol
 */
public record TradingSignal(
    String symbol,
    LocalDate date,
    SignalType signalType,
    int score,                    // 0-100 score
    SignalStrength strength,
    BigDecimal currentPrice,
    String reasoning,             // Human-readable explanation
    SignalComponents components   // Breakdown of the score
) {
    
    public TradingSignal {
        Objects.requireNonNull(symbol, "Symbol cannot be null");
        Objects.requireNonNull(date, "Date cannot be null");
        Objects.requireNonNull(signalType, "Signal type cannot be null");
        
        if (score < 0 || score > 100) {
            throw new IllegalArgumentException("Score must be between 0 and 100");
        }
    }
    
    public enum SignalType {
        BUY, SELL, HOLD
    }
    
    public enum SignalStrength {
        STRONG,     // 80-100
        MODERATE,   // 60-79
        WEAK,       // 40-59
        AVOID       // 0-39
    }
    
    /**
     * Determine signal strength from score
     */
    public static SignalStrength strengthFromScore(int score) {
        if (score >= 80) return SignalStrength.STRONG;
        if (score >= 60) return SignalStrength.MODERATE;
        if (score >= 40) return SignalStrength.WEAK;
        return SignalStrength.AVOID;
    }
    
    /**
     * Check if this is a tradeable signal (score >= 60)
     */
    public boolean isTradeable() {
        return score >= 60;
    }
    
    /**
     * Check if this is a strong signal (score >= 80)
     */
    public boolean isStrong() {
        return score >= 80;
    }
}
