package com.trading.indicators;

import com.trading.domain.IndicatorResult;
import com.trading.domain.MarketData;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Rate of Change (ROC) Indicator
 * 
 * Measures the percentage change in price over N periods.
 * Pure momentum indicator - shows strength of price movement.
 * 
 * Formula: ROC = ((Current Price - Price N periods ago) / Price N periods ago) × 100
 * 
 * Interpretation:
 * - ROC > 10%  → Very strong upward momentum
 * - ROC > 5%   → Strong upward momentum
 * - ROC > 0%   → Positive momentum
 * - ROC < -5%  → Strong downward momentum
 * - ROC < -10% → Very strong downward momentum
 */
public class RateOfChange implements Indicator {
    
    private final int period;
    
    /**
     * Create ROC with standard 10-period
     */
    public RateOfChange() {
        this(10);
    }
    
    /**
     * Create ROC with custom period
     */
    public RateOfChange(int period) {
        if (period < 1) {
            throw new IllegalArgumentException("ROC period must be at least 1");
        }
        this.period = period;
    }
    
    @Override
    public List<IndicatorResult> calculate(List<MarketData> marketData) {
        if (marketData.size() < period + 1) {
            throw new IllegalArgumentException(
                String.format("Need at least %d data points for ROC, got %d", 
                    period + 1, marketData.size())
            );
        }
        
        String symbol = marketData.get(0).symbol();
        List<IndicatorResult> results = new ArrayList<>();
        
        // Calculate ROC starting from period+1
        for (int i = period; i < marketData.size(); i++) {
            BigDecimal currentPrice = marketData.get(i).close().value();
            BigDecimal priorPrice = marketData.get(i - period).close().value();
            
            // ROC = ((Current - Prior) / Prior) * 100
            BigDecimal roc = currentPrice.subtract(priorPrice)
                .divide(priorPrice, 10, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);
            
            results.add(IndicatorResult.of(
                symbol,
                marketData.get(i).date(),
                getName(),
                roc
            ));
        }
        
        return results;
    }
    
    @Override
    public String getName() {
        return "ROC_" + period;
    }
    
    @Override
    public int getMinDataPoints() {
        return period + 1;
    }
    
    /**
     * Interpret ROC value
     */
    public static String interpret(BigDecimal roc) {
        BigDecimal absRoc = roc.abs();
        
        if (roc.compareTo(BigDecimal.valueOf(10)) > 0) {
            return "VERY_STRONG_BULLISH";
        } else if (roc.compareTo(BigDecimal.valueOf(5)) > 0) {
            return "STRONG_BULLISH";
        } else if (roc.compareTo(BigDecimal.ZERO) > 0) {
            return "BULLISH";
        } else if (roc.compareTo(BigDecimal.valueOf(-5)) < 0) {
            return "STRONG_BEARISH";
        } else if (roc.compareTo(BigDecimal.valueOf(-10)) < 0) {
            return "VERY_STRONG_BEARISH";
        } else {
            return "NEUTRAL";
        }
    }
}