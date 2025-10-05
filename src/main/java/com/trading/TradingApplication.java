package com.trading;

import com.trading.config.DatabaseConfig;
import com.trading.data.collector.YahooFinanceCollector;
import com.trading.data.repository.IndicatorRepository;        // ← ADD THIS IMPORT
import com.trading.data.repository.MarketDataRepository;
import com.trading.domain.MarketData;
import com.trading.domain.TradingSignal;
import com.trading.service.*;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Main application entry point
 */
public class TradingApplication {

    private static final Logger logger = LoggerFactory.getLogger(TradingApplication.class);

    // FTSE 100 stocks to monitor
    private static final List<String> TRADING_UNIVERSE = List.of(
            // ========== FTSE 100 LARGE-CAPS (Top 50 by liquidity) ==========
            // Energy
            "SHEL.L", "BP.L", "ENT.L",

            // Financials & Banking
            "HSBA.L", "LLOY.L", "BARC.L", "NWG.L", "STAN.L", "LSEG.L", "III.L", "PRU.L",
//            "AVIV.L",

            // Healthcare & Pharma
            "AZN.L", "GSK.L", "DGE.L", "RKT.L", "OCDO.L",

            // Consumer Goods & Retail
            "ULVR.L", "DGE.L", "BATS.L", "ABF.L", "SBRY.L", "TSCO.L", "MKS.L", "BRBY.L", "NXT.L",

            // Mining & Materials
            "RIO.L", "AAL.L", "GLEN.L", "MNDI.L", "CRH.L", "ANTO.L",

            // Utilities & Infrastructure
            "NG.L", "SSE.L",
//            "SCTN.L",
            "UU.L", "SVT.L",

            // Technology & Services
            "REL.L", "EXPN.L", "AUTO.L", "SAGE.L", "CRDA.L",

            // Telecoms & Media
            "VOD.L", "BT-A.L", "WPP.L",

            // Real Estate & Construction
            "LAND.L", "PSN.L",
//            "SEGRO.L",
            "BNZL.L",

            // Aerospace & Defense
            "BA.L", "RR.L",

            // ========== UK BROAD MARKET ETFS ==========
            "VUKE.L",    // Vanguard FTSE 100
            "VMID.L",    // Vanguard FTSE 250
            "VHYL.L",    // Vanguard UK High Dividend Yield
            "ISF.L",     // iShares Core FTSE 100
            "IUKD.L",    // iShares UK Dividend
//            "VMUK.L",    // Vanguard FTSE All-Share

            // ========== US & NORTH AMERICA ETFS ==========
            "VUSA.L",    // Vanguard S&P 500
            "VUAG.L",    // Vanguard S&P 500 Accumulation
            "CSP1.L",    // iShares Core S&P 500
            "IITU.L",    // iShares MSCI World Tech
//            "QDVX.L",    // iShares NASDAQ 100
            "ISPY.L",    // iShares S&P 500 Tech Sector
            "IUHC.L",    // iShares S&P 500 Healthcare
            "IUFS.L",    // iShares S&P 500 Financials

            // ========== GLOBAL & INTERNATIONAL ETFS ==========
            "VWRL.L",    // Vanguard All-World
            "VWRP.L",    // Vanguard All-World Accumulation
            "SWDA.L",    // iShares Core MSCI World
            "IWQU.L",    // iShares MSCI World Quality Factor
            "IGWD.L",    // iShares MSCI World Value Factor

            // ========== EUROPE ETFS ==========
            "VEUR.L",    // Vanguard FTSE Europe
            "VERX.L",    // Vanguard European ex-UK
//            "IEUE.L",    // iShares Core MSCI Europe
            "VMEU.L",    // Vanguard EUR Eurozone

            // ========== ASIA & EMERGING MARKETS ==========
            "VFEM.L",    // Vanguard Emerging Markets
            "EMIM.L",    // iShares Core MSCI EM IMI
            "VJPN.L",    // Vanguard Japan
//            "VCHA.L",    // Vanguard China
//            "CSI1.L",    // iShares China Large Cap
//            "ISPA.L",    // iShares MSCI Pacific ex-Japan

            // ========== SECTOR & THEMATIC ETFS ==========
            // Technology & Innovation
            "IITU.L",    // World Tech (duplicate but key)
            "RBTX.L",    // iShares Automation & Robotics
//            "IDRV.L",    // iShares Electric Vehicles

            // Clean Energy & ESG
            "INRG.L",    // iShares Clean Energy
//            "DHER.L",    // L&G Hydrogen Economy
            "RENW.L",    // VanEck Low Carbon Energy

            // Healthcare & Biotech
            "HEAL.L",    // Lyxor Healthcare
            "SBIO.L",    // SPDR S&P Biotech

            // Consumer & Retail
            "ISPY.L",    // Consumer Tech

            // Financials & Banks
//            "SUBFIN.L",  // European Financials

            // ========== COMMODITIES & ALTERNATIVES ==========
            // Gold
            "SGLN.L",    // iShares Physical Gold
            "PHGP.L",    // WisdomTree Physical Gold
            "SGLP.L",    // iShares Gold Producers

            // Silver & Precious Metals
            "SSLN.L",    // iShares Physical Silver
//            "PALL.L",    // WisdomTree Physical Palladium
//            "PPLT.L",    // WisdomTree Physical Platinum

            // Energy Commodities
            "CRUD.L",    // WisdomTree Crude Oil
            "AIGA.L",    // WisdomTree Natural Gas

            // Agriculture
            "AIGA.L",    // Agriculture

            // Broad Commodities
            "AIGC.L",    // WisdomTree Enhanced Commodity

            // ========== BONDS & FIXED INCOME ==========
            // UK Government Bonds
            "IGLT.L",    // iShares UK Gilts 0-5yr
            "GILS.L",    // iShares UK Gilts All Stocks
            "IGLH.L",    // iShares UK Gilts 15-25yr (long duration)

            // UK Corporate Bonds
            "SLXX.L",    // iShares Core £ Corporate Bond
            "VGOV.L",    // Vanguard UK Government Bond

            // Global Bonds
            "VAGP.L",    // Vanguard Global Aggregate Bond
            "VAGS.L",    // Vanguard Global Short-Term Bond

            // High Yield
            "GHYG.L",    // iShares Global High Yield Corporate Bond

            // ========== REAL ESTATE & INFRASTRUCTURE ==========
            "IPRP.L",    // iShares UK Property
            "TREI.L",    // WisdomTree Global Quality Real Estate
//            "SUST.L",    // iShares Global Infrastructure

            // ========== CURRENCY HEDGED (for GBP investors) ==========
            "IGUS.L",    // iShares S&P 500 GBP Hedged
            "IGWD.L"     // iShares World GBP Hedged
    );

    public static void main(String[] args) {
        logger.info("Starting UK Trading System...");

        try {
            // Initialize database
            DataSource dataSource = DatabaseConfig.getDataSource();
            logger.info("Database connection established");

            // ========== ADD THESE LINES ==========
            // Initialize repositories and services
            MarketDataRepository marketDataRepo = new MarketDataRepository(dataSource);
            IndicatorRepository indicatorRepo = new IndicatorRepository(dataSource);
            IndicatorService indicatorService = new IndicatorService(marketDataRepo, indicatorRepo);
            YahooFinanceCollector yahooCollector = new YahooFinanceCollector();
            // =====================================

            // CHANGE THIS: minusMonths(9) instead of minusMonths(1) for more data
            LocalDate endDate = LocalDate.now();
            LocalDate startDate = endDate.minusMonths(9); // ← CHANGE FROM 1 to 9 months

            logger.info("Fetching market data from {} to {}", startDate, endDate);

            // Step 1: Fetch market data
            for (String symbol : TRADING_UNIVERSE) {
                try {
                    logger.info("Processing {}", symbol);

                    List<MarketData> marketData = yahooCollector.fetchHistoricalData(
                            symbol, startDate, endDate
                    );

                    if (!marketData.isEmpty()) {
                        marketDataRepo.saveBatch(marketData);
                        logger.info("Saved {} data points for {}", marketData.size(), symbol);
                    }

                    // Rate limiting - be nice to Yahoo Finance
                    Thread.sleep(500);

                } catch (Exception e) {
                    logger.error("Failed to process {}: {}", symbol, e.getMessage());
                }
            }

            // ========== ADD THESE LINES (Step 2: Calculate Indicators) ==========
            logger.info("\n=== Calculating Technical Indicators ===");
            indicatorService.calculateForSymbols(TRADING_UNIVERSE);
            // ====================================================================

            // ========== ADD THESE LINES (Step 3: Display Summaries) ==========
            logger.info("\n=== Indicator Summaries ===");
            for (String symbol : TRADING_UNIVERSE) {
                try {
                    IndicatorService.IndicatorSummary summary =
                            indicatorService.getLatestIndicatorSummary(symbol);
                    summary.print();
                } catch (Exception e) {
                    logger.error("Failed to get summary for {}: {}", symbol, e.getMessage());
                }
            }
            // ================================================================

            // ========== ADD THESE LINES (Step 4: Generate Trading Signals) ==========
            logger.info("\n=== Generating Trading Signals ===");
            SignalGenerator signalGenerator = new SignalGenerator(indicatorService, marketDataRepo);

            // Generate signals for all stocks
            List<TradingSignal> allSignals = signalGenerator.generateSignals(TRADING_UNIVERSE);

            // Print full report
            signalGenerator.printSignalReport(allSignals);

            // Get top buy opportunities
            List<TradingSignal> topBuys = signalGenerator.getTopBuySignals(TRADING_UNIVERSE, 3);
            if (!topBuys.isEmpty()) {
                logger.info("\n🎯 Top 3 Buy Opportunities:");
                for (int i = 0; i < topBuys.size(); i++) {
                    TradingSignal signal = topBuys.get(i);
                    logger.info("  {}. {} - Score: {}/100 - {}",
                            i + 1, signal.symbol(), signal.score(), signal.reasoning());
                }
            }
            // =========================================================================

            // ========== ADD THESE LINES (Step 5: Calculate Position Sizes) ==========
            logger.info("\n=== Position Sizing for Top Opportunities ===");

            // Initialize position sizer with £20,000 portfolio
            BigDecimal portfolioValue = BigDecimal.valueOf(20000);
            RiskParameters riskParams = RiskParameters.moderate();  // Moderate risk
            PositionSizer positionSizer = new PositionSizer(
                    portfolioValue,
                    PositionSizer.PositionSizingStrategy.SIGNAL_STRENGTH,  // Scale with signal score
                    riskParams
            );

            // Calculate position sizes for tradeable signals
            List<TradingSignal> tradeableSignals = signalGenerator.getTradeableSignals(TRADING_UNIVERSE);
            logger.info("Found {} tradeable signals (score >= 60)", tradeableSignals.size());

            if (!tradeableSignals.isEmpty()) {
                System.out.println("\n" + "=".repeat(100));
                System.out.println("                         RECOMMENDED POSITIONS");
                System.out.println("=".repeat(100));

                BigDecimal totalInvestment = BigDecimal.ZERO;

                for (TradingSignal signal : tradeableSignals) {
                    PositionSize position = positionSizer.calculatePositionSize(signal);

                    if (position.isValid()) {
                        position.print();
                        totalInvestment = totalInvestment.add(position.investmentAmount());
                    }
                }

                System.out.println("\n" + "-".repeat(100));
                System.out.printf("Portfolio Summary:%n");
                System.out.printf("  Total Capital:    £%.2f%n", portfolioValue);
                System.out.printf("  Total Investment: £%.2f (%.2f%% deployed)%n",
                        totalInvestment,
                        totalInvestment.divide(portfolioValue, 4, BigDecimal.ROUND_HALF_UP)
                                .multiply(BigDecimal.valueOf(100)));
                System.out.printf("  Cash Remaining:   £%.2f%n", portfolioValue.subtract(totalInvestment));
                System.out.printf("  Positions:        %d%n", tradeableSignals.size());
                System.out.println("=".repeat(100) + "\n");
            } else {
                logger.info("⚠️  No tradeable signals at this time. Wait for better opportunities.");
            }
            // =========================================================================

            logger.info("\n✅ Trading system initialized successfully!");

        } catch (Exception e) {
            logger.error("Application error: {}", e.getMessage(), e);
            System.exit(1);
        }
    }
}