package com.trading.service;

import com.trading.domain.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Calculates trading signals by scoring multiple technical indicators
 */
public class SignalScorer {

    private static final Logger logger = LoggerFactory.getLogger(SignalScorer.class);

    /**
     * Generate a trading signal from indicator summary
     */
    public TradingSignal generateSignal(
            String symbol,
            IndicatorService.IndicatorSummary indicators,
            Price currentPrice) {

        if (indicators == null) {
            logger.warn("No indicators available for {}", symbol);
            return createHoldSignal(symbol, currentPrice, "Insufficient data");
        }

        // Calculate individual components
        int trendScore = calculateTrendScore(indicators);
        int momentumScore = calculateMomentumScore(indicators);
        int valueScore = calculateValueScore(indicators);
        int confluenceBonus = calculateConfluenceBonus(trendScore, momentumScore, valueScore);

        SignalComponents components = new SignalComponents(
            trendScore, momentumScore, valueScore, confluenceBonus
        );

        int totalScore = components.totalScore();

        // Determine signal type and strength
        TradingSignal.SignalType signalType = determineSignalType(totalScore, indicators);
        TradingSignal.SignalStrength strength = TradingSignal.strengthFromScore(totalScore);

        // Build reasoning
        String reasoning = buildReasoning(indicators, components, totalScore);

        return new TradingSignal(
            symbol,
            indicators.sma20() != null ? indicators.sma20().date() : java.time.LocalDate.now(),
            signalType,
            totalScore,
            strength,
            currentPrice.value(),
            reasoning,
            components
        );
    }

    /**
     * Calculate trend score (0-30 points)
     */
    private int calculateTrendScore(IndicatorService.IndicatorSummary indicators) {
        int score = 0;

        if (indicators.sma20() == null || indicators.sma50() == null) {
            return 0;
        }

        BigDecimal sma20 = indicators.sma20().value();
        BigDecimal sma50 = indicators.sma50().value();
        BigDecimal sma200 = indicators.sma200() != null ? indicators.sma200().value() : null;

        // Strong uptrend: SMA(20) > SMA(50) > SMA(200)
        if (sma200 != null && sma20.compareTo(sma50) > 0 && sma50.compareTo(sma200) > 0) {
            score = 30;
        }
        // Moderate uptrend: SMA(20) > SMA(50)
        else if (sma20.compareTo(sma50) > 0) {
            score = 20;
        }
        // Weak bullish: just SMA(20) slightly above SMA(50)
        else if (sma20.compareTo(sma50.multiply(BigDecimal.valueOf(0.99))) > 0) {
            score = 10;
        }

        return score;
    }

    /**
     * Calculate momentum score (0-30 points)
     * Uses both MACD and ROC for comprehensive momentum assessment
     */
    private int calculateMomentumScore(IndicatorService.IndicatorSummary indicators) {
        int macdScore = 0;
        int rocScore = 0;

        // MACD contribution (0-15 points)
        if (indicators.macd() != null) {
            BigDecimal histogram = indicators.macd().value();

            // Strong positive momentum
            if (histogram.compareTo(BigDecimal.ZERO) > 0) {
                if (histogram.abs().compareTo(BigDecimal.valueOf(10)) > 0) {
                    macdScore = 15;  // Very strong
                } else {
                    macdScore = 10;  // Moderate positive
                }
            }
            // Slightly positive (early momentum)
            else if (histogram.compareTo(BigDecimal.valueOf(-5)) > 0) {
                macdScore = 5;
            }
        }

        // ROC contribution (0-15 points)
        if (indicators.roc10() != null) {
            BigDecimal roc = indicators.roc10().value();

            // Very strong upward momentum
            if (roc.compareTo(BigDecimal.valueOf(10)) > 0) {
                rocScore = 15;
            }
            // Strong upward momentum
            else if (roc.compareTo(BigDecimal.valueOf(5)) > 0) {
                rocScore = 12;
            }
            // Moderate positive momentum
            else if (roc.compareTo(BigDecimal.valueOf(2)) > 0) {
                rocScore = 8;
            }
            // Slight positive momentum
            else if (roc.compareTo(BigDecimal.ZERO) > 0) {
                rocScore = 5;
            }
        }

        // Combine MACD and ROC (max 30 points)
        return Math.min(macdScore + rocScore, 30);
    }

    /**
     * Calculate value score (0-30 points, context-aware)
     *
     * CRITICAL FIX: Don't penalize overbought RSI if momentum is strong!
     * RSI > 70 in a trending market = continuation, not exhaustion
     */
    private int calculateValueScore(IndicatorService.IndicatorSummary indicators) {
        if (indicators.rsi() == null) {
            return 0;
        }

        BigDecimal rsi = indicators.rsi().value();
        boolean strongMomentum = hasStrongMomentum(indicators);

        // Oversold - great value
        if (rsi.compareTo(BigDecimal.valueOf(30)) < 0) {
            return 30;
        }
        // Slightly oversold
        else if (rsi.compareTo(BigDecimal.valueOf(40)) < 0) {
            return 20;
        }
        // Overbought WITH strong momentum = trend continuation (BULLISH!)
        else if (rsi.compareTo(BigDecimal.valueOf(70)) > 0 && strongMomentum) {
            return 15;  // Reward strength in trending markets
        }
        // Overbought WITHOUT momentum = exhaustion (BEARISH)
        else if (rsi.compareTo(BigDecimal.valueOf(70)) > 0) {
            return -20;  // Penalty only if no momentum
        }
        // Neutral range
        else if (rsi.compareTo(BigDecimal.valueOf(60)) <= 0) {
            return 10;
        }

        return 5;  // Slightly bullish (60-70 range)
    }

    /**
     * Check if asset has strong momentum (for context-aware RSI)
     */
    private boolean hasStrongMomentum(IndicatorService.IndicatorSummary indicators) {
        // Check ROC first (most direct momentum measure)
        if (indicators.roc10() != null) {
            BigDecimal roc = indicators.roc10().value();
            if (roc.compareTo(BigDecimal.valueOf(5)) > 0) {
                return true;  // ROC > 5% = strong momentum
            }
        }

        // Check MACD histogram
        if (indicators.macd() != null) {
            BigDecimal histogram = indicators.macd().value();
            if (histogram.compareTo(BigDecimal.valueOf(5)) > 0) {
                return true;
            }
        }

        // Check trend alignment (SMA20 well above SMA50)
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
     * Calculate confluence bonus (0-10 points)
     * Awarded when all signals align
     */
    private int calculateConfluenceBonus(int trendScore, int momentumScore, int valueScore) {
        // All three components are positive (aligned bullish signals)
        if (trendScore > 0 && momentumScore > 0 && valueScore > 0) {
            return 10;
        }
        return 0;
    }

    /**
     * Determine signal type from score and indicators
     */
    private TradingSignal.SignalType determineSignalType(
            int score,
            IndicatorService.IndicatorSummary indicators) {

        // Strong buy signals
        if (score >= 60) {
            return TradingSignal.SignalType.BUY;
        }

        // Sell signals (overbought or breaking down)
        if (indicators.rsi() != null &&
            indicators.rsi().value().compareTo(BigDecimal.valueOf(75)) > 0) {
            return TradingSignal.SignalType.SELL;
        }

        if (indicators.getTrendSignal().equals("BEARISH") &&
            score < 30) {
            return TradingSignal.SignalType.SELL;
        }

        return TradingSignal.SignalType.HOLD;
    }

    /**
     * Build human-readable reasoning
     */
    private String buildReasoning(
            IndicatorService.IndicatorSummary indicators,
            SignalComponents components,
            int totalScore) {

        List<String> reasons = new ArrayList<>();

        // Trend reasoning
        if (components.trendScore() >= 20) {
            reasons.add("Strong uptrend (SMA20 > SMA50)");
        } else if (components.trendScore() > 0) {
            reasons.add("Weak bullish trend");
        } else {
            reasons.add("Bearish or no clear trend");
        }

        // Momentum reasoning (now includes ROC)
        if (components.momentumScore() >= 20) {
            String rocInfo = "";
            if (indicators.roc10() != null) {
                rocInfo = String.format(" (ROC: %.1f%%)", indicators.roc10().value());
            }
            reasons.add("Strong momentum" + rocInfo);
        } else if (components.momentumScore() >= 10) {
            reasons.add("Moderate momentum building");
        } else if (components.momentumScore() > 0) {
            reasons.add("Early momentum");
        } else {
            reasons.add("Negative or weak momentum");
        }

        // Value reasoning (context-aware)
        if (indicators.rsi() != null) {
            String rsiSignal = indicators.getRSISignal();
            BigDecimal rsi = indicators.rsi().value();

            if (rsiSignal.equals("OVERSOLD")) {
                reasons.add("Oversold - good value");
            } else if (rsiSignal.equals("OVERBOUGHT")) {
                boolean strongMomentum = hasStrongMomentum(indicators);
                if (strongMomentum) {
                    reasons.add(String.format("Strong trend (RSI %.0f - continuation)", rsi));
                } else {
                    reasons.add("OVERBOUGHT - caution!");
                }
            } else {
                reasons.add("Neutral valuation");
            }
        }

        // Confluence
        if (components.confluenceBonus() > 0) {
            reasons.add("All signals aligned!");
        }

        return String.join(", ", reasons) + " | Score: " + components.breakdown();
    }

    /**
     * Create a default HOLD signal
     */
    private TradingSignal createHoldSignal(String symbol, Price price, String reason) {
        return new TradingSignal(
            symbol,
            java.time.LocalDate.now(),
            TradingSignal.SignalType.HOLD,
            0,
            TradingSignal.SignalStrength.AVOID,
            price.value(),
            reason,
            new SignalComponents(0, 0, 0, 0)
        );
    }
}