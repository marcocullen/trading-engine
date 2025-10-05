package com.trading.indicators;

import com.trading.domain.IndicatorResult;
import com.trading.domain.MarketData;
import com.trading.domain.Price;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Exponential Moving Average (EMA) Indicator
 * Formula: EMA(today) = (Price × α) + (EMA(yesterday) × (1 - α))
 * Where: α = 2 / (period + 1)  [smoothing factor]
 * The EMA gives more weight to recent prices, making it more responsive
 * to new information compared to SMA.
 */
public class ExponentialMovingAverage implements Indicator {
    
    private final int period;
    private final BigDecimal smoothingFactor;  // α (alpha)
    
    /**
     * Create an EMA indicator with the specified period
     * @param period Number of periods (e.g., 12, 26, 50)
     */
    public ExponentialMovingAverage(int period) {
        if (period < 2) {
            throw new IllegalArgumentException("EMA period must be at least 2");
        }
        this.period = period;
        // Calculate smoothing factor: α = 2 / (period + 1)
        this.smoothingFactor = BigDecimal.valueOf(2)
            .divide(BigDecimal.valueOf(period + 1), 10, RoundingMode.HALF_UP);
    }
    
    @Override
    public List<IndicatorResult> calculate(List<MarketData> marketData) {
        if (marketData.size() < period) {
            throw new IllegalArgumentException(
                String.format("Need at least %d data points, got %d", period, marketData.size())
            );
        }
        
        List<IndicatorResult> results = new ArrayList<>();
        String symbol = marketData.getFirst().symbol();
        
        // Initialize EMA with SMA for the first period
        BigDecimal previousEMA = calculateInitialSMA(marketData, period);
        
        // Add the initial EMA value (at position period-1)
        results.add(IndicatorResult.of(
            symbol,
            marketData.get(period - 1).date(),
            getName(),
            previousEMA
        ));
        
        // Calculate EMA for remaining data points
        for (int i = period; i < marketData.size(); i++) {
            BigDecimal currentPrice = marketData.get(i).close().value();
            
            // EMA = (Price × α) + (Previous EMA × (1 - α))
            BigDecimal ema = currentPrice.multiply(smoothingFactor)
                .add(previousEMA.multiply(BigDecimal.ONE.subtract(smoothingFactor)));
            
            ema = ema.setScale(6, RoundingMode.HALF_UP);
            
            results.add(IndicatorResult.of(
                symbol,
                marketData.get(i).date(),
                getName(),
                ema
            ));
            
            previousEMA = ema;
        }
        
        return results;
    }
    
    @Override
    public String getName() {
        return "EMA_" + period;
    }
    
    @Override
    public int getMinDataPoints() {
        return period;
    }
    
    /**
     * Calculate initial SMA to seed the EMA calculation
     */
    private BigDecimal calculateInitialSMA(List<MarketData> data, int period) {
        BigDecimal sum = BigDecimal.ZERO;
        for (int i = 0; i < period; i++) {
            sum = sum.add(data.get(i).close().value());
        }
        return sum.divide(BigDecimal.valueOf(period), 10, RoundingMode.HALF_UP);
    }
    
    /**
     * Utility: Calculate EMA series from price list
     * Returns list of EMA values (same length as input after warmup)
     */
    public static List<BigDecimal> calculateSeries(List<Price> prices, int period) {
        if (prices.size() < period) {
            throw new IllegalArgumentException("Not enough prices for EMA calculation");
        }
        
        List<BigDecimal> emas = new ArrayList<>();
        BigDecimal alpha = BigDecimal.valueOf(2)
            .divide(BigDecimal.valueOf(period + 1), 10, RoundingMode.HALF_UP);
        
        // Initialize with SMA
        BigDecimal sum = BigDecimal.ZERO;
        for (int i = 0; i < period; i++) {
            sum = sum.add(prices.get(i).value());
        }
        BigDecimal previousEMA = sum.divide(BigDecimal.valueOf(period), 10, RoundingMode.HALF_UP);
        emas.add(previousEMA);
        
        // Calculate EMA for rest
        for (int i = period; i < prices.size(); i++) {
            BigDecimal ema = prices.get(i).value().multiply(alpha)
                .add(previousEMA.multiply(BigDecimal.ONE.subtract(alpha)))
                .setScale(6, RoundingMode.HALF_UP);
            emas.add(ema);
            previousEMA = ema;
        }
        
        return emas;
    }
}