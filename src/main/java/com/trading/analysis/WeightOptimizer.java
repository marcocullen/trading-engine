package com.trading.analysis;

import com.trading.data.repository.IndicatorRepository;
import com.trading.data.repository.MarketDataRepository;
import com.trading.domain.*;
import com.trading.service.IndicatorService;
import com.trading.service.SignalScorer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

/**
 * Uses regression analysis to find optimal weights for signal components
 * instead of arbitrary equal weighting (30/30/30)
 */
public class WeightOptimizer {
    
    private static final Logger logger = LoggerFactory.getLogger(WeightOptimizer.class);
    
    private final MarketDataRepository marketDataRepo;
    private final IndicatorRepository indicatorRepo;
    private final IndicatorService indicatorService;
    private final SignalScorer scorer;
    
    public WeightOptimizer(
            MarketDataRepository marketDataRepo,
            IndicatorRepository indicatorRepo,
            IndicatorService indicatorService) {
        this.marketDataRepo = marketDataRepo;
        this.indicatorRepo = indicatorRepo;
        this.indicatorService = indicatorService;
        this.scorer = new SignalScorer();
    }
    
    /**
     * Optimal weights result
     */
    public record OptimalWeights(
            double trendWeight,      // 0-100
            double momentumWeight,   // 0-100
            double valueWeight,      // 0-100
            double confluenceWeight, // 0-100
            double rSquared,         // Model fit (0-1)
            double adjustedRSquared,
            Map<String, Double> rawCoefficients,
            int sampleSize,
            String recommendation
    ) {
        public void print() {
            System.out.println("\n" + "=".repeat(70));
            System.out.println("OPTIMAL SIGNAL WEIGHTS (Regression-Derived)");
            System.out.println("=".repeat(70));
            System.out.printf("Trend Weight:      %.1f/100  (raw β: %.4f)%n", 
                    trendWeight, rawCoefficients.get("trend"));
            System.out.printf("Momentum Weight:   %.1f/100  (raw β: %.4f)%n", 
                    momentumWeight, rawCoefficients.get("momentum"));
            System.out.printf("Value Weight:      %.1f/100  (raw β: %.4f)%n", 
                    valueWeight, rawCoefficients.get("value"));
            System.out.printf("Confluence Weight: %.1f/100  (raw β: %.4f)%n", 
                    confluenceWeight, rawCoefficients.get("confluence"));
            System.out.println("-".repeat(70));
            System.out.printf("R²:                %.4f%n", rSquared);
            System.out.printf("Adjusted R²:       %.4f%n", adjustedRSquared);
            System.out.printf("Sample Size:       %d%n", sampleSize);
            System.out.println("-".repeat(70));
            System.out.printf("Recommendation: %s%n", recommendation);
            System.out.println("=".repeat(70) + "\n");
        }
        
        /**
         * Apply these weights to a SignalComponents
         */
        public int calculateWeightedScore(SignalComponents components) {
            double score = 
                (components.trendScore() / 30.0) * trendWeight +
                (components.momentumScore() / 30.0) * momentumWeight +
                (components.valueScore() / 30.0) * valueWeight +
                (components.confluenceBonus() / 10.0) * confluenceWeight;
            
            return (int) Math.round(score);
        }
    }
    
    /**
     * Find optimal weights using ordinary least squares regression
     * Model: ForwardReturn = β₁*Trend + β₂*Momentum + β₃*Value + β₄*Confluence + ε
     */
    public OptimalWeights findOptimalWeights(
            List<String> symbols,
            LocalDate startDate,
            LocalDate endDate,
            int holdingPeriodDays) {
        
        logger.info("Optimizing signal weights using {} symbols", symbols.size());
        
        // Collect training data
        List<TrainingData> data = collectTrainingData(
                symbols, startDate, endDate, holdingPeriodDays
        );
        
        if (data.size() < 50) {
            logger.warn("Insufficient training data: {} samples", data.size());
            return createDefaultWeights(data.size());
        }
        
        logger.info("Collected {} training samples", data.size());
        
        // Prepare regression matrices
        // X = [trend, momentum, value, confluence]
        // y = [forward returns]
        
        double[][] X = new double[data.size()][4];
        double[] y = new double[data.size()];
        
        for (int i = 0; i < data.size(); i++) {
            TrainingData sample = data.get(i);
            X[i][0] = sample.trendScore;
            X[i][1] = sample.momentumScore;
            X[i][2] = sample.valueScore;
            X[i][3] = sample.confluenceBonus;
            y[i] = sample.forwardReturn.doubleValue();
        }
        
        // Perform OLS regression
        RegressionResult regression = performOLS(X, y);
        
        // Normalize coefficients to 0-100 scale
        double[] coefficients = regression.coefficients;
        double totalWeight = Arrays.stream(coefficients).map(Math::abs).sum();
        
        double trendWeight = (Math.abs(coefficients[0]) / totalWeight) * 100;
        double momentumWeight = (Math.abs(coefficients[1]) / totalWeight) * 100;
        double valueWeight = (Math.abs(coefficients[2]) / totalWeight) * 100;
        double confluenceWeight = (Math.abs(coefficients[3]) / totalWeight) * 100;
        
        // Build recommendation
        String recommendation = buildRecommendation(
                trendWeight, momentumWeight, valueWeight, confluenceWeight,
                regression.rSquared
        );
        
        Map<String, Double> rawCoeffs = Map.of(
                "trend", coefficients[0],
                "momentum", coefficients[1],
                "value", coefficients[2],
                "confluence", coefficients[3]
        );
        
        return new OptimalWeights(
                trendWeight,
                momentumWeight,
                valueWeight,
                confluenceWeight,
                regression.rSquared,
                regression.adjustedRSquared,
                rawCoeffs,
                data.size(),
                recommendation
        );
    }
    
    /**
     * Collect training data (signals + outcomes)
     */
    private List<TrainingData> collectTrainingData(
            List<String> symbols,
            LocalDate startDate,
            LocalDate endDate,
            int holdingPeriodDays) {
        
        List<TrainingData> trainingData = new ArrayList<>();
        
        for (String symbol : symbols) {
            try {
                // Get indicator summaries for each date in range
                LocalDate date = startDate;
                while (date.isBefore(endDate)) {
                    
                    IndicatorService.IndicatorSummary summary = 
                            indicatorService.getLatestIndicatorSummary(symbol);
                    
                    if (summary == null) {
                        date = date.plusDays(1);
                        continue;
                    }
                    
                    Optional<Price> entryPrice = marketDataRepo.getLatestPrice(symbol);
                    if (entryPrice.isEmpty()) {
                        date = date.plusDays(1);
                        continue;
                    }
                    
                    // Generate signal components
                    TradingSignal signal = scorer.generateSignal(
                            symbol, summary, entryPrice.get()
                    );
                    
                    // Get forward return
                    LocalDate exitDate = date.plusDays(holdingPeriodDays);
                    Optional<MarketData> exitData = marketDataRepo
                            .findBySymbolAndDate(symbol, exitDate);
                    
                    if (exitData.isPresent()) {
                        BigDecimal returnPct = exitData.get().close().value()
                                .subtract(entryPrice.get().value())
                                .divide(entryPrice.get().value(), 6, RoundingMode.HALF_UP)
                                .multiply(BigDecimal.valueOf(100));
                        
                        trainingData.add(new TrainingData(
                                signal.components().trendScore(),
                                signal.components().momentumScore(),
                                signal.components().valueScore(),
                                signal.components().confluenceBonus(),
                                returnPct
                        ));
                    }
                    
                    date = date.plusDays(5); // Weekly sampling to avoid autocorrelation
                }
            } catch (Exception e) {
                logger.error("Failed to collect data for {}: {}", symbol, e.getMessage());
            }
        }
        
        return trainingData;
    }
    
    /**
     * Perform ordinary least squares regression
     */
    private RegressionResult performOLS(double[][] X, double[] y) {
        int n = X.length;
        int p = X[0].length;
        
        // Calculate X'X (covariance matrix)
        double[][] XtX = new double[p][p];
        for (int i = 0; i < p; i++) {
            for (int j = 0; j < p; j++) {
                double sum = 0;
                for (int k = 0; k < n; k++) {
                    sum += X[k][i] * X[k][j];
                }
                XtX[i][j] = sum;
            }
        }
        
        // Calculate X'y
        double[] Xty = new double[p];
        for (int i = 0; i < p; i++) {
            double sum = 0;
            for (int j = 0; j < n; j++) {
                sum += X[j][i] * y[j];
            }
            Xty[i] = sum;
        }
        
        // Solve (X'X)β = X'y using Gaussian elimination
        double[] coefficients = solveLinearSystem(XtX, Xty);
        
        // Calculate R²
        double yMean = Arrays.stream(y).average().orElse(0.0);
        double ssTot = Arrays.stream(y)
                .map(yi -> Math.pow(yi - yMean, 2))
                .sum();
        
        double ssRes = 0;
        for (int i = 0; i < n; i++) {
            double prediction = 0;
            for (int j = 0; j < p; j++) {
                prediction += coefficients[j] * X[i][j];
            }
            ssRes += Math.pow(y[i] - prediction, 2);
        }
        
        double rSquared = 1.0 - (ssRes / ssTot);
        double adjustedRSquared = 1.0 - ((1.0 - rSquared) * (n - 1) / (n - p - 1));
        
        return new RegressionResult(coefficients, rSquared, adjustedRSquared);
    }
    
    /**
     * Solve linear system Ax = b using Gaussian elimination
     */
    private double[] solveLinearSystem(double[][] A, double[] b) {
        int n = b.length;
        double[][] augmented = new double[n][n + 1];
        
        // Create augmented matrix [A|b]
        for (int i = 0; i < n; i++) {
            System.arraycopy(A[i], 0, augmented[i], 0, n);
            augmented[i][n] = b[i];
        }
        
        // Forward elimination
        for (int i = 0; i < n; i++) {
            // Partial pivoting
            int maxRow = i;
            for (int k = i + 1; k < n; k++) {
                if (Math.abs(augmented[k][i]) > Math.abs(augmented[maxRow][i])) {
                    maxRow = k;
                }
            }
            double[] temp = augmented[i];
            augmented[i] = augmented[maxRow];
            augmented[maxRow] = temp;
            
            // Eliminate column
            for (int k = i + 1; k < n; k++) {
                double factor = augmented[k][i] / augmented[i][i];
                for (int j = i; j < n + 1; j++) {
                    augmented[k][j] -= factor * augmented[i][j];
                }
            }
        }
        
        // Back substitution
        double[] x = new double[n];
        for (int i = n - 1; i >= 0; i--) {
            double sum = 0;
            for (int j = i + 1; j < n; j++) {
                sum += augmented[i][j] * x[j];
            }
            x[i] = (augmented[i][n] - sum) / augmented[i][i];
        }
        
        return x;
    }
    
    /**
     * Build recommendation text
     */
    private String buildRecommendation(
            double trend, double momentum, double value, double confluence,
            double rSquared) {
        
        if (rSquared < 0.05) {
            return "⚠️ Very weak model fit (R² < 0.05). Indicators may not predict returns. Consider different approach.";
        } else if (rSquared < 0.15) {
            return "⚠️ Weak model fit (R² < 0.15). Use with caution. Consider adding more predictive features.";
        }
        
        List<String> recommendations = new ArrayList<>();
        
        // Find dominant component
        double max = Math.max(Math.max(trend, momentum), Math.max(value, confluence));
        
        if (trend == max) {
            recommendations.add("Trend is the strongest predictor - emphasize trend-following");
        } else if (momentum == max) {
            recommendations.add("Momentum dominates - focus on ROC and MACD signals");
        } else if (value == max) {
            recommendations.add("Value (RSI) is most predictive - mean reversion strategy may work");
        }
        
        if (rSquared > 0.25) {
            recommendations.add(String.format("Good model fit (R²=%.2f)", rSquared));
        }
        
        return String.join(". ", recommendations) + ".";
    }
    
    /**
     * Create default equal weights if insufficient data
     */
    private OptimalWeights createDefaultWeights(int sampleSize) {
        return new OptimalWeights(
                30.0, 30.0, 30.0, 10.0,
                0.0, 0.0,
                Map.of("trend", 30.0, "momentum", 30.0, "value", 30.0, "confluence", 10.0),
                sampleSize,
                "Insufficient data - using default equal weights"
        );
    }
    
    /**
     * Training data point
     */
    private record TrainingData(
            double trendScore,
            double momentumScore,
            double valueScore,
            double confluenceBonus,
            BigDecimal forwardReturn
    ) {}
    
    /**
     * Regression result
     */
    private record RegressionResult(
            double[] coefficients,
            double rSquared,
            double adjustedRSquared
    ) {}
}