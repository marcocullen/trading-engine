package com.trading.service;

import com.trading.analysis.WeightOptimizer;
import com.trading.domain.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Data-driven signal scorer that uses empirically-derived thresholds
 * and regression-optimized weights instead of arbitrary values
 */
public class DataDrivenSignalScorer {
    
    private static final Logger logger = LoggerFactory.getLogger(DataDrivenSignalScorer.class);
    
    // Empirically-derived thresholds (replace these with your analysis results)
    private final BigDecimal rsiOversoldThreshold;
    private final BigDecimal rsiOverboughtThreshold;
    private final BigDecimal rocStrongThreshold;
    private final BigDecimal rocWeakThreshold;
    
    // Regression-derived weights (replace with your optimization results)
    private final WeightOptimizer.OptimalWeights optimalWeights;
    
    /**
     * Constructor with default textbook values
     * (Replace with analyzed values after running StatisticalAnalysisRunner)
     */
    public DataDrivenSignalScorer() {
        this.rsiOversoldThreshold = BigDecimal.valueOf(30);
        this.rsiOverboughtThreshold = BigDecimal.valueOf(70);
        this.rocStrongThreshold = BigDecimal.valueOf(10);
        this.rocWeakThreshold = BigDecimal.valueOf(5);
        
        // Default equal weights (replace after analysis)
        this.optimalWeights = new WeightOptimizer.OptimalWeights(
                30.0, 30.0, 30.0, 10.0,
                0.0, 0.0,
                java.util.Map.of("trend", 30.0, "momentum", 30.0, "value", 30.0, "confluence", 10.0),
                0,
                "Using default weights - run analysis to optimize"
        );
    }
    
    /**
     * Constructor with analyzed thresholds and weights
     */
    public DataDrivenSignalScorer(
            BigDecimal rsiOversoldThreshold,
            BigDecimal rsiOverboughtThreshold,
            BigDecimal rocStrongThreshold,
            BigDecimal rocWeakThreshold,
            WeightOptimizer.OptimalWeights optimalWeights) {
        
        this.rsiOversoldThreshold = rsiOversoldThreshold;
        this.rsiOverboughtThreshold = rsiOverboughtThreshold;
        this.rocStrongThreshold = rocStrongThreshold;
        this.rocWeakThreshold = rocWeakThreshold;
        this.optimalWeights = optimalWeights;
        
        logger.info("Initialized with optimized parameters:");
        logger.info("  RSI Oversold: < {}", rsiOversoldThreshold);
        logger.info("  RSI Overbought: > {}", rsiOverboughtThreshold);
        logger.info("  ROC Strong: > {}", rocStrongThreshold);
        logger.info("  Weights: T={:.1f} M={:.1f} V={:.1f} C={:.1f}", 
                optimalWeights.trendWeight(),
                optimalWeights.momentumWeight(),
                optimalWeights.valueWeight(),
                optimalWeights.confluenceWeight());
    }
    
    /**
     * Generate signal using data-driven approach
     */
    public TradingSignal generateSignal(
            String symbol,
            IndicatorService.IndicatorSummary indicators,
            Price currentPrice) {
        
        if (indicators == null) {
            return createHoldSignal(symbol, currentPrice, "Insufficient data");
        }
        
        // Calculate component scores (0-30 range)
        int trendScore = calculateTrendScore(indicators);
        int momentumScore = calculateMomentumScore(indicators);
        int valueScore = calculateValueScore(indicators);
        int confluenceBonus = calculateConfluenceBonus(trendScore, momentumScore, valueScore);
        
        SignalComponents components = new SignalComponents(
                trendScore, momentumScore, valueScore, confluenceBonus
        );
        
        // Apply optimized weights to get final score (0-100)
        int weightedScore = optimalWeights.calculateWeightedScore(components);
        
        // Determine signal type
        TradingSignal.SignalType signalType = determineSignalType(weightedScore, indicators);
        TradingSignal.SignalStrength strength = TradingSignal.strengthFromScore(weightedScore);
        
        String reasoning = buildReasoning(indicators, components, weightedScore);
        
        return new TradingSignal(
                symbol,
                indicators.sma20() != null ? indicators.sma20().date() : LocalDate.now(),
                signalType,
                weightedScore,
                strength,
                currentPrice.value(),
                reasoning,
                components
        );
    }
    
    /**
     * Calculate trend score (0-30)
     */
    private int calculateTrendScore(IndicatorService.IndicatorSummary indicators) {
        if (indicators.sma20() == null || indicators.sma50() == null) {
            return 0;
        }
        
        BigDecimal sma20 = indicators.sma20().value();
        BigDecimal sma50 = indicators.sma50().value();
        BigDecimal sma200 = indicators.sma200() != null ? indicators.sma200().value() : null;
        
        // Strong uptrend: SMA20 > SMA50 > SMA200
        if (sma200 != null && sma20.compareTo(sma50) > 0 && sma50.compareTo(sma200) > 0) {
            return 30;
        }
        // Moderate uptrend: SMA20 > SMA50
        else if (sma20.compareTo(sma50) > 0) {
            return 20;
        }
        // Weak bullish: SMA20 close to SMA50
        else if (sma20.compareTo(sma50.multiply(BigDecimal.valueOf(0.99))) > 0) {
            return 10;
        }
        
        return 0;
    }
    
    /**
     * Calculate momentum score using data-driven thresholds (0-30)
     */
    private int calculateMomentumScore(IndicatorService.IndicatorSummary indicators) {
        int macdScore = 0;
        int rocScore = 0;
        
        // MACD contribution (0-15)
        if (indicators.macd() != null) {
            BigDecimal histogram = indicators.macd().value();
            
            if (histogram.compareTo(BigDecimal.ZERO) > 0) {
                if (histogram.abs().compareTo(BigDecimal.valueOf(10)) > 0) {
                    macdScore = 15;
                } else {
                    macdScore = 10;
                }
            } else if (histogram.compareTo(BigDecimal.valueOf(-5)) > 0) {
                macdScore = 5;
            }
        }
        
        // ROC contribution using data-driven thresholds (0-15)
        if (indicators.roc10() != null) {
            BigDecimal roc = indicators.roc10().value();
            
            // Use empirically-derived thresholds instead of hardcoded values
            if (roc.compareTo(rocStrongThreshold) > 0) {
                rocScore = 15;  // Very strong (above optimal threshold)
            } else if (roc.compareTo(rocWeakThreshold) > 0) {
                rocScore = 10;  // Moderate
            } else if (roc.compareTo(BigDecimal.ZERO) > 0) {
                rocScore = 5;   // Weak positive
            }
        }
        
        return Math.min(macdScore + rocScore, 30);
    }
    
    /**
     * Calculate value score using data-driven RSI thresholds (0-30)
     */
    private int calculateValueScore(IndicatorService.IndicatorSummary indicators) {
        if (indicators.rsi() == null) {
            return 0;
        }
        
        BigDecimal rsi = indicators.rsi().value();
        boolean strongMomentum = hasStrongMomentum(indicators);
        
        // Use empirically-derived ov   ersold threshold
        if (rsi.compareTo(rsiOversoldThreshold) < 0) {
            return 30;  // Oversold - high value
        }
        // Slightly above oversold threshold
        else if (rsi.compareTo(rsiOversoldThreshold.add(BigDecimal.TEN)) < 0) {
            return 20;
        }
        // Overbought WITH momentum = continuation
        else if (rsi.compareTo(rsiOverboughtThreshold) > 0 && strongMomentum) {
            return 15;  // Strong trend continuation
        }
        // Overbought WITHOUT momentum = exhaustion
        else if (rsi.compareTo(rsiOverboughtThreshold) > 0) {
            return -20; // Penalty for overbought without momentum
        }
        // Neutral range
        else if (rsi.compareTo(BigDecimal.valueOf(60)) <= 0) {
            return 10;
        }
        
        return 5;
    }
    
    /**
     * Check for strong momentum (context for RSI)
     */
    private boolean hasStrongMomentum(IndicatorService.IndicatorSummary indicators) {
        // Use data-driven ROC threshold
        if (indicators.roc10() != null && 
            indicators.roc10().value().compareTo(rocWeakThreshold) > 0) {
            return true;
        }
        
        if (indicators.macd() != null && 
            indicators.macd().value().compareTo(BigDecimal.valueOf(5)) > 0) {
            return true;
        }
        
        if (indicators.sma20() != null && indicators.sma50() != null) {
            BigDecimal diff = indicators.sma20().value().subtract(indicators.sma50().value());
            BigDecimal diffPercent = diff.divide(indicators.sma50().value(), 4, BigDecimal.ROUND_HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
            if (diffPercent.compareTo(BigDecimal.valueOf(2)) > 0) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Calculate confluence bonus (0-10)
     */
    private int calculateConfluenceBonus(int trendScore, int momentumScore, int valueScore) {
        if (trendScore > 0 && momentumScore > 0 && valueScore > 0) {
            return 10;
        }
        return 0;
    }
    
    /**
     * Determine signal type
     */
    private TradingSignal.SignalType determineSignalType(
            int score,
            IndicatorService.IndicatorSummary indicators) {
        
        if (score >= 60) {
            return TradingSignal.SignalType.BUY;
        }
        
        // Sell on extreme overbought
        if (indicators.rsi() != null && 
            indicators.rsi().value().compareTo(BigDecimal.valueOf(75)) > 0) {
            return TradingSignal.SignalType.SELL;
        }
        
        if (indicators.getTrendSignal().equals("BEARISH") && score < 30) {
            return TradingSignal.SignalType.SELL;
        }
        
        return TradingSignal.SignalType.HOLD;
    }
    
    /**
     * Build reasoning string
     */
    private String buildReasoning(
            IndicatorService.IndicatorSummary indicators,
            SignalComponents components,
            int weightedScore) {
        
        List<String> reasons = new ArrayList<>();
        
        // Trend
        if (components.trendScore() >= 20) {
            reasons.add("Strong uptrend");
        } else if (components.trendScore() > 0) {
            reasons.add("Weak bullish trend");
        }
        
        // Momentum
        if (components.momentumScore() >= 20) {
            reasons.add(String.format("Strong momentum (ROC: %.1f%%)", 
                    indicators.roc10() != null ? indicators.roc10().value() : BigDecimal.ZERO));
        }
        
        // Value
        if (indicators.rsi() != null) {
            BigDecimal rsi = indicators.rsi().value();
            if (rsi.compareTo(rsiOversoldThreshold) < 0) {
                reasons.add(String.format("Oversold (RSI: %.0f < %.0f)", rsi, rsiOversoldThreshold));
            } else if (rsi.compareTo(rsiOverboughtThreshold) > 0) {
                reasons.add(String.format("Overbought (RSI: %.0f)", rsi));
            }
        }
        
        if (components.confluenceBonus() > 0) {
            reasons.add("All signals aligned");
        }
        
        return String.join(", ", reasons) + 
                String.format(" | Weighted Score: %d/100 (Weights: %.0f/%.0f/%.0f/%.0f)",
                        weightedScore,
                        optimalWeights.trendWeight(),
                        optimalWeights.momentumWeight(),
                        optimalWeights.valueWeight(),
                        optimalWeights.confluenceWeight());
    }
    
    /**
     * Create hold signal
     */
    private TradingSignal createHoldSignal(String symbol, Price price, String reason) {
        return new TradingSignal(
                symbol,
                LocalDate.now(),
                TradingSignal.SignalType.HOLD,
                0,
                TradingSignal.SignalStrength.AVOID,
                price.value(),
                reason,
                new SignalComponents(0, 0, 0, 0)
        );
    }
}