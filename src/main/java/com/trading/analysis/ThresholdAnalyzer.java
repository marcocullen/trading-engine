package com.trading.analysis;

import com.trading.data.repository.IndicatorRepository;
import com.trading.data.repository.MarketDataRepository;
import com.trading.domain.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

/**
 * Analyzes historical data to find empirically optimal indicator thresholds
 * Replaces arbitrary textbook values with data-driven cutoffs
 */
public class ThresholdAnalyzer {
    
    private static final Logger logger = LoggerFactory.getLogger(ThresholdAnalyzer.class);
    
    private final MarketDataRepository marketDataRepo;
    private final IndicatorRepository indicatorRepo;
    
    public ThresholdAnalyzer(
            MarketDataRepository marketDataRepo,
            IndicatorRepository indicatorRepo) {
        this.marketDataRepo = marketDataRepo;
        this.indicatorRepo = indicatorRepo;
    }
    
    /**
     * Result of threshold analysis
     */
    public record ThresholdAnalysisResult(
            String indicatorName,
            BigDecimal optimalThreshold,
            BigDecimal avgForwardReturn,
            double sharpeRatio,
            int sampleSize,
            double winRate,
            double pValue,
            String interpretation
    ) {
        public void print() {
            System.out.printf("\n=== %s Threshold Analysis ===%n", indicatorName);
            System.out.printf("Optimal Threshold: %.2f%n", optimalThreshold);
            System.out.printf("Avg Forward Return: %.2f%%%n", avgForwardReturn);
            System.out.printf("Sharpe Ratio: %.2f%n", sharpeRatio);
            System.out.printf("Win Rate: %.1f%%%n", winRate * 100);
            System.out.printf("Sample Size: %d%n", sampleSize);
            System.out.printf("P-Value: %.4f %s%n", pValue, 
                    pValue < 0.05 ? "(Significant ✓)" : "(Not Significant ✗)");
            System.out.printf("Interpretation: %s%n", interpretation);
            System.out.println("=".repeat(50));
        }
    }
    
    /**
     * Find optimal RSI oversold threshold
     * Tests values from 20 to 40 to find which predicts best forward returns
     */
    public ThresholdAnalysisResult findOptimalRSIOversold(
            List<String> symbols,
            LocalDate startDate,
            LocalDate endDate,
            int holdingPeriodDays) {
        
        logger.info("Analyzing RSI oversold threshold for {} symbols", symbols.size());
        
        ThresholdAnalysisResult bestResult = null;
        double bestSharpe = Double.NEGATIVE_INFINITY;
        
        // Test thresholds from 20 to 40
        for (int threshold = 20; threshold <= 40; threshold++) {
            BigDecimal thresholdValue = BigDecimal.valueOf(threshold);
            List<BigDecimal> returns = new ArrayList<>();
            
            for (String symbol : symbols) {
                // Get RSI values and forward returns
                List<IndicatorResult> rsiValues = indicatorRepo.findBySymbolAndIndicator(
                        symbol, "RSI_14", startDate, endDate
                );
                
                for (IndicatorResult rsi : rsiValues) {
                    // Check if RSI is below threshold (oversold)
                    if (rsi.value().compareTo(thresholdValue) < 0) {
                        // Get forward return
                        Optional<BigDecimal> forwardReturn = calculateForwardReturn(
                                symbol, rsi.date(), holdingPeriodDays
                        );
                        forwardReturn.ifPresent(returns::add);
                    }
                }
            }
            
            if (returns.size() < 30) continue; // Need statistical significance
            
            // Calculate metrics
            BigDecimal avgReturn = calculateMean(returns);
            BigDecimal stdDev = calculateStdDev(returns, avgReturn);
            double sharpe = stdDev.compareTo(BigDecimal.ZERO) > 0 
                    ? avgReturn.divide(stdDev, 4, RoundingMode.HALF_UP).doubleValue()
                    : 0.0;
            
            long winners = returns.stream()
                    .filter(r -> r.compareTo(BigDecimal.ZERO) > 0)
                    .count();
            double winRate = (double) winners / returns.size();
            
            // T-test for significance
            double pValue = calculatePValue(returns);
            
            String interpretation = String.format(
                    "RSI < %d: %d signals, %.1f%% win rate",
                    threshold, returns.size(), winRate * 100
            );
            
            ThresholdAnalysisResult result = new ThresholdAnalysisResult(
                    "RSI_Oversold",
                    thresholdValue,
                    avgReturn,
                    sharpe,
                    returns.size(),
                    winRate,
                    pValue,
                    interpretation
            );
            
            // Track best Sharpe ratio
            if (sharpe > bestSharpe && pValue < 0.05) {
                bestSharpe = sharpe;
                bestResult = result;
            }
        }
        
        return bestResult;
    }
    
    /**
     * Find optimal ROC threshold for momentum
     */
    public ThresholdAnalysisResult findOptimalROCThreshold(
            List<String> symbols,
            LocalDate startDate,
            LocalDate endDate,
            int holdingPeriodDays) {
        
        logger.info("Analyzing ROC momentum threshold for {} symbols", symbols.size());
        
        ThresholdAnalysisResult bestResult = null;
        double bestSharpe = Double.NEGATIVE_INFINITY;
        
        // Test thresholds from 2% to 15%
        for (int threshold = 2; threshold <= 15; threshold++) {
            BigDecimal thresholdValue = BigDecimal.valueOf(threshold);
            List<BigDecimal> returns = new ArrayList<>();
            
            for (String symbol : symbols) {
                List<IndicatorResult> rocValues = indicatorRepo.findBySymbolAndIndicator(
                        symbol, "ROC_10", startDate, endDate
                );
                
                for (IndicatorResult roc : rocValues) {
                    // Check if ROC is above threshold (strong momentum)
                    if (roc.value().compareTo(thresholdValue) > 0) {
                        Optional<BigDecimal> forwardReturn = calculateForwardReturn(
                                symbol, roc.date(), holdingPeriodDays
                        );
                        forwardReturn.ifPresent(returns::add);
                    }
                }
            }
            
            if (returns.size() < 30) continue;
            
            BigDecimal avgReturn = calculateMean(returns);
            BigDecimal stdDev = calculateStdDev(returns, avgReturn);
            double sharpe = stdDev.compareTo(BigDecimal.ZERO) > 0 
                    ? avgReturn.divide(stdDev, 4, RoundingMode.HALF_UP).doubleValue()
                    : 0.0;
            
            long winners = returns.stream()
                    .filter(r -> r.compareTo(BigDecimal.ZERO) > 0)
                    .count();
            double winRate = (double) winners / returns.size();
            
            double pValue = calculatePValue(returns);
            
            ThresholdAnalysisResult result = new ThresholdAnalysisResult(
                    "ROC_Momentum",
                    thresholdValue,
                    avgReturn,
                    sharpe,
                    returns.size(),
                    winRate,
                    pValue,
                    String.format("ROC > %d%%: %d signals, %.1f%% win rate",
                            threshold, returns.size(), winRate * 100)
            );
            
            if (sharpe > bestSharpe && pValue < 0.05) {
                bestSharpe = sharpe;
                bestResult = result;
            }
        }
        
        return bestResult;
    }
    
    /**
     * Calculate forward return from a given date
     */
    private Optional<BigDecimal> calculateForwardReturn(
            String symbol,
            LocalDate signalDate,
            int holdingPeriodDays) {
        
        LocalDate exitDate = signalDate.plusDays(holdingPeriodDays);
        
        Optional<MarketData> entryData = marketDataRepo.findBySymbolAndDate(symbol, signalDate);
        Optional<MarketData> exitData = marketDataRepo.findBySymbolAndDate(symbol, exitDate);
        
        if (entryData.isEmpty() || exitData.isEmpty()) {
            return Optional.empty();
        }
        
        BigDecimal entryPrice = entryData.get().close().value();
        BigDecimal exitPrice = exitData.get().close().value();
        
        BigDecimal returnPct = exitPrice.subtract(entryPrice)
                .divide(entryPrice, 6, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
        
        return Optional.of(returnPct);
    }
    
    /**
     * Calculate mean of returns
     */
    private BigDecimal calculateMean(List<BigDecimal> values) {
        if (values.isEmpty()) return BigDecimal.ZERO;
        
        BigDecimal sum = values.stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        return sum.divide(BigDecimal.valueOf(values.size()), 6, RoundingMode.HALF_UP);
    }
    
    /**
     * Calculate standard deviation
     */
    private BigDecimal calculateStdDev(List<BigDecimal> values, BigDecimal mean) {
        if (values.size() < 2) return BigDecimal.ZERO;
        
        BigDecimal sumSquaredDiff = values.stream()
                .map(v -> v.subtract(mean).pow(2))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        BigDecimal variance = sumSquaredDiff.divide(
                BigDecimal.valueOf(values.size() - 1), 10, RoundingMode.HALF_UP
        );
        
        return BigDecimal.valueOf(Math.sqrt(variance.doubleValue()));
    }
    
    /**
     * Calculate p-value using one-sample t-test
     * H0: mean return = 0
     * H1: mean return != 0
     */
    private double calculatePValue(List<BigDecimal> returns) {
        if (returns.size() < 2) return 1.0;
        
        BigDecimal mean = calculateMean(returns);
        BigDecimal stdDev = calculateStdDev(returns, mean);
        
        if (stdDev.compareTo(BigDecimal.ZERO) == 0) return 1.0;
        
        // t-statistic = mean / (stdDev / sqrt(n))
        double tStat = mean.doubleValue() / 
                (stdDev.doubleValue() / Math.sqrt(returns.size()));
        
        // Approximate p-value (two-tailed)
        // For proper implementation, use Apache Commons Math TDistribution
        int df = returns.size() - 1;
        double pValue = 2.0 * (1.0 - normalCDF(Math.abs(tStat)));
        
        return Math.max(0.0, Math.min(1.0, pValue));
    }
    
    /**
     * Approximate normal CDF (for p-value calculation)
     */
    private double normalCDF(double z) {
        return 0.5 * (1.0 + erf(z / Math.sqrt(2.0)));
    }
    
    /**
     * Error function approximation
     */
    private double erf(double z) {
        double t = 1.0 / (1.0 + 0.5 * Math.abs(z));
        double tau = t * Math.exp(-z * z - 1.26551223 +
                t * (1.00002368 +
                t * (0.37409196 +
                t * (0.09678418 +
                t * (-0.18628806 +
                t * (0.27886807 +
                t * (-1.13520398 +
                t * (1.48851587 +
                t * (-0.82215223 +
                t * 0.17087277)))))))));
        return z >= 0 ? 1 - tau : tau - 1;
    }
}