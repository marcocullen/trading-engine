package com.trading.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Represents a trade (buy or sell action)
 */
public record Trade(
    String tradeId,
    String symbol,
    TradeAction action,
    BigDecimal quantity,
    Price price,
    LocalDate executedAt,
    String strategyName,
    boolean isPaperTrade
) {
    public Trade {
        Objects.requireNonNull(tradeId, "Trade ID cannot be null");
        Objects.requireNonNull(symbol, "Symbol cannot be null");
        Objects.requireNonNull(action, "Trade action cannot be null");
        Objects.requireNonNull(quantity, "Quantity cannot be null");
        Objects.requireNonNull(price, "Price cannot be null");
        Objects.requireNonNull(executedAt, "Execution date cannot be null");
        
        if (quantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Trade quantity must be positive");
        }
    }
    
    public enum TradeAction {
        BUY, SELL
    }
    
    /**
     * Calculate total trade value (excluding commission)
     */
    public Price totalValue() {
        return price.multiply(quantity);
    }
}