package com.trading.domain;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Represents a position in the portfolio
 */
public record Position(
    String symbol,
    BigDecimal quantity,
    Price averageCost,
    Price currentPrice
) {
    public Position {
        Objects.requireNonNull(symbol, "Symbol cannot be null");
        Objects.requireNonNull(quantity, "Quantity cannot be null");
        Objects.requireNonNull(averageCost, "Average cost cannot be null");
        
        if (quantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }
    }
    
    /**
     * Calculate current market value of position
     */
    public Price marketValue() {
        if (currentPrice == null) {
            return averageCost.multiply(quantity);
        }
        return currentPrice.multiply(quantity);
    }
    
    /**
     * Calculate unrealized P&L
     */
    public Price unrealizedPnL() {
        if (currentPrice == null) {
            return new Price(BigDecimal.ZERO);
        }
        Price totalCost = averageCost.multiply(quantity);
        return marketValue().subtract(totalCost);
    }
    
    /**
     * Calculate percentage return
     */
    public BigDecimal percentageReturn() {
        if (currentPrice == null) {
            return BigDecimal.ZERO;
        }
        return currentPrice.percentageChange(averageCost);
    }
}