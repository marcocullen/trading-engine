package com.trading.service;

import com.trading.data.repository.MarketDataRepository;
import com.trading.domain.Price;
import com.trading.domain.TradingSignal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class SignalGenerator {

    private static final Logger logger = LoggerFactory.getLogger(SignalGenerator.class);

    private final IndicatorService indicatorService;
    private final MarketDataRepository marketDataRepo;
    private final SignalScorer scorer;

    // Basic asset class mapping for correlation check
    private static final Map<String, String> ASSET_CLASS_MAP = Map.ofEntries(
            Map.entry("SGLN.L", "GOLD"),
            Map.entry("PHGP.L", "GOLD"),
            Map.entry("SGLP.L", "GOLD"),
            Map.entry("SSLN.L", "SILVER"),
            Map.entry("PALL.L", "PRECIOUS_METALS"),
            Map.entry("PPLT.L", "PRECIOUS_METALS"),
            Map.entry("HSBA.L", "UK_BANKS"),
            Map.entry("BARC.L", "UK_BANKS"),
            Map.entry("LLOY.L", "UK_BANKS"),
            Map.entry("SHEL.L", "ENERGY"),
            Map.entry("BP.L", "ENERGY"),
            Map.entry("CRUD.L", "ENERGY")
    );

    public SignalGenerator(
            IndicatorService indicatorService,
            MarketDataRepository marketDataRepo) {
        this.indicatorService = indicatorService;
        this.marketDataRepo = marketDataRepo;
        this.scorer = new SignalScorer();
    }

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

    public TradingSignal generateSignal(String symbol) {
        IndicatorService.IndicatorSummary indicators =
                indicatorService.getLatestIndicatorSummary(symbol);

        Price currentPrice = marketDataRepo.getLatestPrice(symbol)
                .orElseThrow(() -> new RuntimeException("No price data for " + symbol));

        return scorer.generateSignal(symbol, indicators, currentPrice);
    }

    public List<TradingSignal> getTopBuySignals(List<String> symbols, int limit) {
        List<TradingSignal> allSignals = generateSignals(symbols);

        return allSignals.stream()
                .filter(s -> s.signalType() == TradingSignal.SignalType.BUY)
                .sorted(Comparator.comparingInt(TradingSignal::score).reversed())
                .limit(limit)
                .toList();
    }

    public List<TradingSignal> getTradeableSignals(List<String> symbols) {
        List<TradingSignal> allSignals = generateSignals(symbols);

        List<TradingSignal> tradeable = allSignals.stream()
                .filter(TradingSignal::isTradeable)
                .sorted(Comparator.comparingInt(TradingSignal::score).reversed())
                .toList();

        // Check correlation after filtering
        if (!tradeable.isEmpty()) {
            checkCorrelation(tradeable);
        }

        return tradeable;
    }

    /**
     * Check for over-concentration in asset classes
     */
    private void checkCorrelation(List<TradingSignal> signals) {
        Map<String, Long> assetClassCount = signals.stream()
                .collect(Collectors.groupingBy(
                        s -> ASSET_CLASS_MAP.getOrDefault(s.symbol(), "OTHER"),
                        Collectors.counting()
                ));

        long totalPositions = signals.size();
        for (Map.Entry<String, Long> entry : assetClassCount.entrySet()) {
            double percentage = (entry.getValue() * 100.0) / totalPositions;
            if (percentage > 30 && !entry.getKey().equals("OTHER")) {
                logger.warn("⚠️ {}% exposure to {} (recommended limit: 30%)",
                        Math.round(percentage), entry.getKey());
            }
        }
    }

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
