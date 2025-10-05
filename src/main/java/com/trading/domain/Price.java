package com.trading.domain;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Price value object with BigDecimal precision
 * Immutable and comparable
 */
public record Price(BigDecimal value) implements Comparable<Price> {
    
    public Price {
        Objects.requireNonNull(value, "Price value cannot be null");
        if (value.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Price cannot be negative: " + value);
        }
    }
    
    /**
     * Create a Price from a double (for convenience in tests/parsing)
     */
    public static Price of(double value) {
        return new Price(BigDecimal.valueOf(value));
    }
    
    /**
     * Create a Price from a String
     */
    public static Price of(String value) {
        return new Price(new BigDecimal(value));
    }
    
    /**
     * Add two prices
     */
    public Price add(Price other) {
        return new Price(this.value.add(other.value));
    }
    
    /**
     * Subtract another price
     */
    public Price subtract(Price other) {
        return new Price(this.value.subtract(other.value));
    }
    
    /**
     * Multiply by a factor
     */
    public Price multiply(BigDecimal factor) {
        return new Price(this.value.multiply(factor));
    }
    
    /**
     * Divide by a divisor
     */
    public Price divide(BigDecimal divisor) {
        return new Price(this.value.divide(divisor, 4, BigDecimal.ROUND_HALF_UP));
    }
    
    /**
     * Calculate percentage change from another price
     */
    public BigDecimal percentageChange(Price from) {
        if (from.value.compareTo(BigDecimal.ZERO) == 0) {
            throw new ArithmeticException("Cannot calculate percentage change from zero price");
        }
        return this.value.subtract(from.value)
                .divide(from.value, 6, BigDecimal.ROUND_HALF_UP)
                .multiply(BigDecimal.valueOf(100));
    }
    
    @Override
    public int compareTo(Price other) {
        return this.value.compareTo(other.value);
    }
    
    @Override
    public String toString() {
        return value.toPlainString();
    }
}