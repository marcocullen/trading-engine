package com.trading.indicators;

import com.trading.domain.IndicatorResult;
import com.trading.domain.MarketData;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * RSI (Relative Strength Index) Indicator
 * 
 * Formula:
 * 1. Calculate price changes (gains and losses)
 * 2. Average Gain = SMA of gains over period
 * 3. Average Loss = SMA of losses over period
 * 4. RS = Average Gain / Average Loss
 * 5. RSI = 100 - (100 / (1 + RS))
 * 
 * Interpretation:
 * - RSI > 70 → Overbought (potential sell)
 * - RSI < 30 → Oversold (potential buy)
 * - RSI around 50 → Neutral
 */
public class RSI implements Indicator {
    
    private final int period;
    
    /**
     * Create RSI with standard 14-period
     */
    public RSI() {
        this(14);
    }
    
    /**
     * Create RSI with custom period
     */
    public RSI(int period) {
        if (period < 2) {
            throw new IllegalArgumentException("RSI period must be at least 2");
        }
        this.period = period;
    }
    
    @Override
    public List<IndicatorResult> calculate(List<MarketData> marketData) {
        // Need period + 1 data points (one extra for first price change)
        if (marketData.size() < period + 1) {
            throw new IllegalArgumentException(
                String.format("Need at least %d data points for RSI, got %d", 
                    period + 1, marketData.size())
            );
        }
        
        String symbol = marketData.get(0).symbol();
        List<IndicatorResult> results = new ArrayList<>();
        
        // Calculate price changes
        List<BigDecimal> gains = new ArrayList<>();
        List<BigDecimal> losses = new ArrayList<>();
        
        for (int i = 1; i < marketData.size(); i++) {
            BigDecimal change = marketData.get(i).close().value()
                .subtract(marketData.get(i - 1).close().value());
            
            if (change.compareTo(BigDecimal.ZERO) > 0) {
                gains.add(change);
                losses.add(BigDecimal.ZERO);
            } else {
                gains.add(BigDecimal.ZERO);
                losses.add(change.abs());
            }
        }
        
        // Calculate initial average gain and loss (SMA)
        BigDecimal avgGain = calculateAverage(gains.subList(0, period));
        BigDecimal avgLoss = calculateAverage(losses.subList(0, period));
        
        // Calculate and store first RSI value
        BigDecimal firstRSI = calculateRSI(avgGain, avgLoss);
        results.add(IndicatorResult.of(
            symbol,
            marketData.get(period).date(),
            getName(),
            firstRSI
        ));
        
        // Calculate subsequent RSI values using smoothed averages
        for (int i = period; i < gains.size(); i++) {
            // Smoothed average: ((previous avg × (period - 1)) + current value) / period
            avgGain = avgGain.multiply(BigDecimal.valueOf(period - 1))
                .add(gains.get(i))
                .divide(BigDecimal.valueOf(period), 10, RoundingMode.HALF_UP);
            
            avgLoss = avgLoss.multiply(BigDecimal.valueOf(period - 1))
                .add(losses.get(i))
                .divide(BigDecimal.valueOf(period), 10, RoundingMode.HALF_UP);
            
            BigDecimal rsi = calculateRSI(avgGain, avgLoss);
            
            results.add(IndicatorResult.of(
                symbol,
                marketData.get(i + 1).date(),  // i+1 because gains/losses are offset by 1
                getName(),
                rsi
            ));
        }
        
        return results;
    }
    
    @Override
    public String getName() {
        return "RSI_" + period;
    }
    
    @Override
    public int getMinDataPoints() {
        return period + 1;
    }
    
    /**
     * Calculate RSI from average gain and loss
     */
    private BigDecimal calculateRSI(BigDecimal avgGain, BigDecimal avgLoss) {
        // Handle edge case where avgLoss is zero
        if (avgLoss.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.valueOf(100);
        }
        
        // RS = avgGain / avgLoss
        BigDecimal rs = avgGain.divide(avgLoss, 10, RoundingMode.HALF_UP);
        
        // RSI = 100 - (100 / (1 + RS))
        BigDecimal rsi = BigDecimal.valueOf(100)
            .subtract(
                BigDecimal.valueOf(100)
                    .divide(BigDecimal.ONE.add(rs), 10, RoundingMode.HALF_UP)
            );
        
        return rsi.setScale(2, RoundingMode.HALF_UP);
    }
    
    /**
     * Calculate simple average of a list
     */
    private BigDecimal calculateAverage(List<BigDecimal> values) {
        if (values.isEmpty()) {
            return BigDecimal.ZERO;
        }
        
        BigDecimal sum = values.stream()
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        return sum.divide(BigDecimal.valueOf(values.size()), 10, RoundingMode.HALF_UP);
    }
    
    /**
     * Interpret RSI value
     */
    public static String interpret(BigDecimal rsi) {
        if (rsi.compareTo(BigDecimal.valueOf(70)) > 0) {
            return "OVERBOUGHT";
        } else if (rsi.compareTo(BigDecimal.valueOf(30)) < 0) {
            return "OVERSOLD";
        } else {
            return "NEUTRAL";
        }
    }
}