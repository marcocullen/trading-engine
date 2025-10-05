package com.trading.domain;

/**
 * Breakdown of signal score components
 */
public record SignalComponents(
    int trendScore,        // 0-30
    int momentumScore,     // 0-30
    int valueScore,        // 0-30
    int confluenceBonus    // 0-10
) {
    public int totalScore() {
        return trendScore + momentumScore + valueScore + confluenceBonus;
    }
    
    public String breakdown() {
        return String.format(
            "Trend: %d/30, Momentum: %d/30, Value: %d/30, Bonus: %d/10 = %d/100",
            trendScore, momentumScore, valueScore, confluenceBonus, totalScore()
        );
    }
}