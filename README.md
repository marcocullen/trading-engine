# UK Trading System

> **A data-driven trading decision support system for UK equity markets**
>
> Building a systematic approach to trading UK equities and ETFs using technical analysis, economic indicators, and sentiment analysis.

---

## 📊 Project Overview

**Goal**: Create an algorithmic trading system for a £20,000 portfolio focused on UK equities and ETFs.

**Investment Philosophy**:
- Long-only positions (no CFDs, no complex derivatives)
- Data-driven decisions using technical indicators and economic data
- Systematic rebalancing based on calculated signals
- Risk management through position sizing and diversification

**Timeline**:
- **Weeks 1-2**: Core infrastructure + paper trading
- **Weeks 3-4**: Live paper trading validation
- **Week 5+**: Real trading with systematic scaling

---

## 🏗️ Architecture

```
┌─────────────────────────────────────────────────┐
│           UK TRADING SYSTEM                     │
├─────────────────────────────────────────────────┤
│                                                 │
│  📥 DATA INGESTION LAYER                       │
│  ├── Yahoo Finance (EOD prices, OHLCV)         │
│  ├── Bank of England API (interest rates)      │
│  ├── ONS API (UK economic data)                │
│  ├── Reddit API (sentiment)                    │
│  └── Google Gemini (LLM analysis)              │
│                                                 │
│  💾 DATA STORAGE                               │
│  └── PostgreSQL (time-series optimized)        │
│                                                 │
│  🧮 ANALYSIS ENGINE                            │
│  ├── Technical Indicators (MACD, RSI, SMA...)  │
│  ├── Economic Signal Processing                │
│  └── Sentiment Scoring                         │
│                                                 │
│  🎯 DECISION ENGINE                            │
│  ├── Multi-factor Scoring                     │
│  ├── Portfolio Optimizer                       │
│  └── Risk Calculator                           │
│                                                 │
│  📈 EXECUTION LAYER                            │
│  ├── Paper Trading Simulator                   │
│  ├── Trade Order Generator                     │
│  └── Portfolio Rebalancer                      │
│                                                 │
└─────────────────────────────────────────────────┘
```

---

## 🛠️ Tech Stack

- **Language**: Java 21 (records, pattern matching, virtual threads)
- **Build Tool**: Gradle 8.x
- **Database**: PostgreSQL 16 with HikariCP connection pooling
- **Migration**: Flyway
- **HTTP Client**: OkHttp
- **JSON**: Gson & Jackson
- **Logging**: SLF4J + Logback
- **Testing**: JUnit 5, AssertJ, Mockito

---

## 📈 Trading Universe

### FTSE 100 Core Holdings
| Symbol    | Company                    | Sector      |
|-----------|----------------------------|-------------|
| SHEL.L    | Shell                      | Energy      |
| BP.L      | BP                         | Energy      |
| AZN.L     | AstraZeneca               | Pharma      |
| GSK.L     | GSK                       | Pharma      |
| HSBA.L    | HSBC                      | Banking     |
| ULVR.L    | Unilever                  | Consumer    |
| DGE.L     | Diageo                    | Consumer    |
| RIO.L     | Rio Tinto                 | Mining      |
| BATS.L    | British American Tobacco  | Tobacco     |
| NG.L      | National Grid             | Utilities   |

### ETF Universe
- **VUKE.L**: Vanguard FTSE 100 UCITS ETF
- **ISF.L**: iShares Core FTSE 100
- **SGLN.L**: iShares Physical Gold (commodity exposure)
- **VUSA.L**: Vanguard S&P 500 (US exposure via UK listing)

---

## 🚦 Project Status

### ✅ Phase 1: Core Infrastructure (COMPLETE)
- [x] Project setup with Gradle
- [x] PostgreSQL database with schema
- [x] Domain models (Instrument, MarketData, Price, Position, Trade)
- [x] Market data repository with batch operations
- [x] Yahoo Finance API integration
- [x] Historical data collection (1 month FTSE 100)

### 🚧 Phase 2: Technical Analysis (IN PROGRESS)
- [ ] Simple Moving Average (SMA)
- [ ] Exponential Moving Average (EMA)
- [ ] MACD (Moving Average Convergence Divergence)
- [ ] RSI (Relative Strength Index)
- [ ] Bollinger Bands
- [ ] Momentum indicators
- [ ] Indicator persistence layer

### 📋 Phase 3: Decision Engine (PLANNED)
- [ ] Multi-factor scoring algorithm
- [ ] Economic data integration (BoE, ONS)
- [ ] Sentiment analysis (Reddit, news)
- [ ] Portfolio allocation optimizer
- [ ] Risk management rules
- [ ] Signal generation pipeline

### 📋 Phase 4: Execution (PLANNED)
- [ ] Paper trading simulator
- [ ] Trade order generation
- [ ] Portfolio rebalancing logic
- [ ] Performance tracking
- [ ] Strategy backtesting
- [ ] Live trading integration

---

## 🚀 Quick Start

### Prerequisites
- Java 21+
- Docker & Docker Compose
- Gradle 8.x (or use wrapper)

### Setup

1. **Clone and setup project structure**
   ```bash
   git clone <your-repo>
   cd uk-trading-system
   ```

2. **Start PostgreSQL**
   ```bash
   docker-compose up -d
   ```

3. **Run database migrations**
   ```bash
   ./gradlew flywayMigrate
   ```

4. **Build the project**
   ```bash
   ./gradlew build
   ```

5. **Run data collection**
   ```bash
   ./gradlew run
   ```

### Verify Data Collection

Connect to PostgreSQL and check:
```sql
-- See latest market data
SELECT symbol, trade_date, close_price, volume 
FROM market_data 
ORDER BY trade_date DESC 
LIMIT 20;

-- Check data coverage
SELECT symbol, COUNT(*) as days, MIN(trade_date) as from_date, MAX(trade_date) as to_date
FROM market_data 
GROUP BY symbol 
ORDER BY symbol;
```

---

## 📚 Technical Indicators (Learning Guide)

### Simple Moving Average (SMA)
**Purpose**: Smooth price data to identify trends  
**Formula**: `SMA = (P1 + P2 + ... + Pn) / n`  
**Usage**:
- SMA(50) > SMA(200) = **Bullish** (Golden Cross)
- SMA(50) < SMA(200) = **Bearish** (Death Cross)

### Exponential Moving Average (EMA)
**Purpose**: More responsive to recent price changes  
**Formula**: `EMA = Price(today) × k + EMA(yesterday) × (1 - k)` where `k = 2/(n+1)`  
**Usage**: Faster signal generation than SMA

### MACD (Moving Average Convergence Divergence)
**Purpose**: Trend direction and momentum  
**Formula**:
- MACD Line = EMA(12) - EMA(26)
- Signal Line = EMA(9) of MACD Line
- Histogram = MACD Line - Signal Line

**Signals**:
- MACD crosses above Signal = **Buy**
- MACD crosses below Signal = **Sell**

### RSI (Relative Strength Index)
**Purpose**: Overbought/oversold conditions  
**Formula**: `RSI = 100 - (100 / (1 + RS))` where `RS = Avg Gain / Avg Loss`  
**Signals**:
- RSI > 70 = **Overbought** (consider selling)
- RSI < 30 = **Oversold** (consider buying)

### Bollinger Bands
**Purpose**: Volatility and price extremes  
**Formula**:
- Middle Band = SMA(20)
- Upper Band = SMA(20) + (2 × StdDev)
- Lower Band = SMA(20) - (2 × StdDev)

**Signals**:
- Price touches upper band = Overbought
- Price touches lower band = Oversold

---

## 🗂️ Project Structure

```
uk-trading-system/
├── build.gradle                    # Project dependencies
├── settings.gradle                 # Gradle settings
├── docker-compose.yml              # PostgreSQL container
├── README.md                       # This file
│
├── src/main/
│   ├── java/com/trading/
│   │   ├── domain/                 # Core domain models
│   │   │   ├── Instrument.java
│   │   │   ├── MarketData.java
│   │   │   ├── Price.java
│   │   │   ├── Position.java
│   │   │   └── Trade.java
│   │   │
│   │   ├── data/
│   │   │   ├── repository/         # Data access layer
│   │   │   │   ├── MarketDataRepository.java
│   │   │   │   └── IndicatorRepository.java
│   │   │   └── collector/          # External data collection
│   │   │       ├── DataCollector.java
│   │   │       ├── YahooFinanceCollector.java
│   │   │       ├── EconomicDataCollector.java
│   │   │       └── SentimentCollector.java
│   │   │
│   │   ├── indicators/             # Technical analysis
│   │   │   ├── Indicator.java
│   │   │   ├── SMA.java
│   │   │   ├── EMA.java
│   │   │   ├── MACD.java
│   │   │   ├── RSI.java
│   │   │   └── BollingerBands.java
│   │   │
│   │   ├── strategy/               # Trading strategies
│   │   │   ├── Strategy.java
│   │   │   ├── TrendFollowing.java
│   │   │   └── MeanReversion.java
│   │   │
│   │   ├── execution/              # Trade execution
│   │   │   ├── PaperTradingEngine.java
│   │   │   ├── Portfolio.java
│   │   │   └── OrderGenerator.java
│   │   │
│   │   ├── config/                 # Configuration
│   │   │   └── DatabaseConfig.java
│   │   │
│   │   └── TradingApplication.java # Main entry point
│   │
│   └── resources/
│       ├── application.properties  # App configuration
│       └── db/migration/           # Flyway migrations
│           └── V1__initial_schema.sql
│
└── src/test/
    └── java/com/trading/
        ├── indicators/             # Indicator tests
        └── strategy/               # Strategy backtests
```

---

## 💡 Design Principles

### KISS (Keep It Simple, Stupid)
- Prefer clarity over cleverness
- Simple algorithms before complex ML
- Readable code over premature optimization

### Financial Precision
- Always use `BigDecimal` for monetary values
- Explicit rounding modes for all calculations
- Handle currency conversions carefully
- Immutable domain models to prevent data corruption

### Risk Management
- Position sizing (max 5-10% per position)
- Stop-loss rules (trailing stops)
- Maximum drawdown limits
- Portfolio diversification across sectors

### Testing & Validation
- Unit tests for all indicators
- Backtesting with historical data
- Paper trading before live execution
- Performance metrics tracking

---

## 📊 Database Schema

### Core Tables

**market_data**: OHLCV data for all instruments
```sql
symbol, trade_date, open_price, high_price, low_price, 
close_price, adjusted_close, volume
```

**technical_indicators**: Calculated indicator values
```sql
symbol, trade_date, indicator_name, value, metadata
```

**portfolio_positions**: Current holdings
```sql
symbol, quantity, average_cost, current_price, unrealized_pnl
```

**trade_history**: Complete trade audit trail
```sql
trade_id, symbol, action, quantity, price, strategy_name, 
is_paper_trade, executed_at
```

**economic_indicators**: UK economic data
```sql
indicator_name, indicator_date, value, source
```

**sentiment_data**: News & social sentiment
```sql
symbol, sentiment_date, source, sentiment_score, summary
```

---

## 📈 Trading Strategy (Planned)

### Multi-Factor Scoring System

Each stock receives a score based on:

1. **Technical Score (40%)**
    - MACD signal strength
    - RSI positioning
    - Price vs moving averages
    - Momentum

2. **Economic Score (30%)**
    - UK interest rate trends
    - GDP growth
    - Sector-specific indicators
    - Currency strength

3. **Sentiment Score (20%)**
    - News sentiment
    - Reddit/social media buzz
    - Analyst recommendations

4. **Risk Score (10%)**
    - Volatility (Bollinger Band width)
    - Beta vs FTSE 100
    - Maximum drawdown

### Portfolio Allocation Rules
- Top 5-10 scored stocks
- Max 10% per position
- Rebalance weekly/monthly based on signals
- Cash buffer: 10-20% for opportunities

---

## 🔬 Testing & Validation

### Backtesting Framework
```bash
# Run backtest on historical data
./gradlew backtest --start=2024-01-01 --end=2024-12-31

# Paper trading simulation
./gradlew paperTrade --duration=14days
```

### Key Metrics to Track
- **Sharpe Ratio**: Risk-adjusted returns
- **Maximum Drawdown**: Largest peak-to-trough decline
- **Win Rate**: Percentage of profitable trades
- **Profit Factor**: Gross profit / gross loss
- **Alpha**: Returns vs FTSE 100 benchmark

---

## 🎯 Success Criteria

**Week 2 Goals:**
- ✅ Core infrastructure operational
- ✅ Data collection automated
- 🎯 Technical indicators calculated
- 🎯 Basic scoring algorithm

**Week 4 Goals:**
- 🎯 Paper trading live for 2 weeks
- 🎯 Positive Sharpe ratio (>1.0)
- 🎯 Maximum drawdown < 10%
- 🎯 Confidence interval: 95%+

**Week 6 Goals:**
- 🎯 First live trade executed
- 🎯 Portfolio tracking operational
- 🎯 Risk limits enforced automatically

---

## 📚 Learning Resources

### Technical Analysis
- [Investopedia - Technical Indicators](https://www.investopedia.com/terms/t/technicalindicator.asp)
- [TradingView - Indicator Documentation](https://www.tradingview.com/pine-script-docs/en/v5/)

### UK Market Data
- [Bank of England API](https://www.bankofengland.co.uk/boeapps/database/)
- [ONS API Documentation](https://developer.ons.gov.uk/)

### Algorithmic Trading
- "Algorithmic Trading" by Ernest Chan
- "Quantitative Trading" by Ernest Chan

---

## 🤝 Contributing

This is a personal learning project, but insights welcome!

### Development Workflow
1. Create feature branch
2. Implement with tests
3. Update graph memory with learnings
4. Document in README
5. Merge to main

---

## ⚠️ Risk Disclaimer

**This is an educational project.**

- Past performance does not guarantee future results
- All trading involves risk of loss
- Start with paper trading
- Only invest what you can afford to lose
- Seek professional financial advice

---

## 📝 Next Steps

### Immediate (This Week)
1. ✅ Complete Phase 1 infrastructure
2. 🔨 Implement technical indicators (SMA, EMA, MACD, RSI)
3. 🔨 Create indicator calculation pipeline
4. 🔨 Persist indicator values to database

### Short-term (Week 2)
1. Build decision engine
2. Implement paper trading simulator
3. Integrate economic data
4. Test strategy on historical data

### Medium-term (Weeks 3-4)
1. Live paper trading
2. Performance tracking dashboard
3. Refine algorithms based on results
4. Build confidence for live trading

---

## 📞 Contact & Support

**Project Lead**: Principal Software Engineer  
**Focus**: Quantitative methods upskilling  
**Timeline**: 2 weeks to paper trading, 4 weeks to live trading

---

*Last Updated: October 2025*  
*Version: 1.0.0-SNAPSHOT*