-- Market Data Table
CREATE TABLE market_data (
    id BIGSERIAL PRIMARY KEY,
    symbol VARCHAR(20) NOT NULL,
    trade_date DATE NOT NULL,
    open_price NUMERIC(18, 4) NOT NULL,
    high_price NUMERIC(18, 4) NOT NULL,
    low_price NUMERIC(18, 4) NOT NULL,
    close_price NUMERIC(18, 4) NOT NULL,
    adjusted_close NUMERIC(18, 4) NOT NULL,
    volume BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(symbol, trade_date)
);

CREATE INDEX idx_market_data_symbol_date ON market_data(symbol, trade_date DESC);
CREATE INDEX idx_market_data_date ON market_data(trade_date DESC);

-- Technical Indicators Table
CREATE TABLE technical_indicators (
    id BIGSERIAL PRIMARY KEY,
    symbol VARCHAR(20) NOT NULL,
    trade_date DATE NOT NULL,
    indicator_name VARCHAR(50) NOT NULL,
    value NUMERIC(18, 6),
    metadata JSONB, -- For storing additional indicator parameters
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(symbol, trade_date, indicator_name)
);

CREATE INDEX idx_indicators_symbol_date ON technical_indicators(symbol, trade_date DESC);
CREATE INDEX idx_indicators_name ON technical_indicators(indicator_name);

-- Economic Indicators Table
CREATE TABLE economic_indicators (
    id BIGSERIAL PRIMARY KEY,
    indicator_name VARCHAR(100) NOT NULL,
    indicator_date DATE NOT NULL,
    value NUMERIC(18, 6) NOT NULL,
    source VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(indicator_name, indicator_date, source)
);

CREATE INDEX idx_econ_indicators_date ON economic_indicators(indicator_date DESC);

-- Portfolio Positions Table
CREATE TABLE portfolio_positions (
    id BIGSERIAL PRIMARY KEY,
    symbol VARCHAR(20) NOT NULL,
    quantity NUMERIC(18, 8) NOT NULL,
    average_cost NUMERIC(18, 4) NOT NULL,
    current_price NUMERIC(18, 4),
    current_value NUMERIC(18, 4),
    unrealized_pnl NUMERIC(18, 4),
    last_updated TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_closed BOOLEAN DEFAULT FALSE,
    UNIQUE(symbol)
);

-- Trade History Table
CREATE TABLE trade_history (
    id BIGSERIAL PRIMARY KEY,
    trade_id VARCHAR(50) NOT NULL UNIQUE,
    symbol VARCHAR(20) NOT NULL,
    action VARCHAR(10) NOT NULL CHECK (action IN ('BUY', 'SELL')),
    quantity NUMERIC(18, 8) NOT NULL,
    price NUMERIC(18, 4) NOT NULL,
    total_value NUMERIC(18, 4) NOT NULL,
    commission NUMERIC(18, 4) DEFAULT 0,
    strategy_name VARCHAR(100),
    is_paper_trade BOOLEAN DEFAULT FALSE,
    executed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    notes TEXT
);

CREATE INDEX idx_trade_history_symbol ON trade_history(symbol, executed_at DESC);
CREATE INDEX idx_trade_history_date ON trade_history(executed_at DESC);

-- Sentiment Data Table
CREATE TABLE sentiment_data (
    id BIGSERIAL PRIMARY KEY,
    symbol VARCHAR(20),
    sentiment_date DATE NOT NULL,
    source VARCHAR(50) NOT NULL,
    sentiment_score NUMERIC(5, 2), -- -1.00 to 1.00 scale
    summary TEXT,
    raw_data JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_sentiment_symbol_date ON sentiment_data(symbol, sentiment_date DESC);
CREATE INDEX idx_sentiment_source ON sentiment_data(source, sentiment_date DESC);

-- Stocks/Instruments Master Table
CREATE TABLE instruments (
    symbol VARCHAR(20) PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    exchange VARCHAR(50) NOT NULL,
    asset_type VARCHAR(50) NOT NULL CHECK (asset_type IN ('STOCK', 'ETF', 'INDEX')),
    sector VARCHAR(100),
    currency VARCHAR(3) DEFAULT 'GBP',
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Comments for documentation
COMMENT ON TABLE market_data IS 'Stores daily OHLCV market data for stocks and ETFs';
COMMENT ON TABLE technical_indicators IS 'Stores calculated technical indicator values';
COMMENT ON TABLE portfolio_positions IS 'Current portfolio holdings and positions';
COMMENT ON TABLE trade_history IS 'Complete audit trail of all trades (paper and real)';
COMMENT ON TABLE sentiment_data IS 'Sentiment analysis from news, social media, and LLMs';