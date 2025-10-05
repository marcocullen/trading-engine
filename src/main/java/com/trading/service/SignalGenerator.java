package com.trading.service;

import com.trading.data.repository.MarketDataRepository;
import com.trading.domain.Price;
import com.trading.domain.TradingSignal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Generates trading signals for a universe of stocks
 */
public class SignalGenerator {
    
    private static final Logger logger = LoggerFactory.getLogger(SignalGenerator.class);
    
    private final IndicatorService indicatorService;
    private final MarketDataRepository marketDataRepo;
    private final SignalScorer scorer;
    
    public SignalGenerator(
            IndicatorService indicatorService,
            MarketDataRepository marketDataRepo) {
        this.indicatorService = indicatorService;
        this.marketDataRepo = marketDataRepo;
        this.scorer = new SignalScorer();
    }
    
    /**
     * Generate signals for all symbols
     */
    public List<TradingSignal> generateSignals(List<String> symbols) {
        logger.info("Generating signals for {} symbols", symbols.size());
        
        List<TradingSignal> signals = new ArrayList<>();
        
        for (String symbol : symbols) {
            try {
                TradingSignal signal = generateSignal(symbol);
                signals.add(signal);
                
                logger.info("{}: {} - Score: {} - {}", 
                    symbol, 
                    signal.signalType(), 
                    signal.score(),
                    signal.strength());
                
            } catch (Exception e) {
                logger.error("Failed to generate signal for {}: {}", symbol, e.getMessage());
            }
        }
        
        return signals;
    }
    
    /**
     * Generate signal for a single symbol
     */
    public TradingSignal generateSignal(String symbol) {
        // Get latest indicators
        IndicatorService.IndicatorSummary indicators = 
            indicatorService.getLatestIndicatorSummary(symbol);
        
        // Get current price
        Price currentPrice = marketDataRepo.getLatestPrice(symbol)
            .orElseThrow(() -> new RuntimeException("No price data for " + symbol));
        
        // Generate signal
        return scorer.generateSignal(symbol, indicators, currentPrice);
    }
    
    /**
     * Get top buy opportunities sorted by score
     */
    public List<TradingSignal> getTopBuySignals(List<String> symbols, int limit) {
        List<TradingSignal> allSignals = generateSignals(symbols);
        
        return allSignals.stream()
            .filter(s -> s.signalType() == TradingSignal.SignalType.BUY)
            .sorted(Comparator.comparingInt(TradingSignal::score).reversed())
            .limit(limit)
            .toList();
    }
    
    /**
     * Get signals that meet a minimum score threshold
     */
    public List<TradingSignal> getTradeableSignals(List<String> symbols) {
        List<TradingSignal> allSignals = generateSignals(symbols);
        
        return allSignals.stream()
            .filter(TradingSignal::isTradeable)  // Score >= 60
            .sorted(Comparator.comparingInt(TradingSignal::score).reversed())
            .toList();
    }
    
    /**
     * Print formatted signal report
     */
    public void printSignalReport(List<TradingSignal> signals) {
        System.out.println("\n" + "=".repeat(100));
        System.out.println("                            TRADING SIGNAL REPORT");
        System.out.println("=".repeat(100));
        
        for (TradingSignal signal : signals) {
            String emoji = switch (signal.signalType()) {
                case BUY -> "🟢";
                case SELL -> "🔴";
                case HOLD -> "⚪";
            };
            
            System.out.printf("\n%s %s | %s | Score: %d/100 | Price: £%.2f%n",
                emoji,
                signal.symbol(),
                signal.signalType(),
                signal.score(),
                signal.currentPrice());
            
            System.out.printf("   Strength: %s%n", signal.strength());
            System.out.printf("   %s%n", signal.reasoning());
            
            if (signal.isTradeable()) {
                System.out.println("   ✅ TRADEABLE SIGNAL");
            }
        }
        
        System.out.println("\n" + "=".repeat(100));
        
        // Summary stats
        long buyCount = signals.stream()
            .filter(s -> s.signalType() == TradingSignal.SignalType.BUY)
            .count();
        long sellCount = signals.stream()
            .filter(s -> s.signalType() == TradingSignal.SignalType.SELL)
            .count();
        long tradeableCount = signals.stream()
            .filter(TradingSignal::isTradeable)
            .count();
        
        System.out.printf("Summary: %d BUY | %d SELL | %d HOLD | %d Tradeable (≥60 score)%n",
            buyCount, sellCount, signals.size() - buyCount - sellCount, tradeableCount);
        System.out.println("=".repeat(100) + "\n");
    }
}