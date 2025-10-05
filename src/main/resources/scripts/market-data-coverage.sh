docker exec -it trading-postgres psql -U trading_user -d trading_db -c "
SELECT
    symbol,
    COUNT(*) as data_points,
    MIN(trade_date) as first_date,
    MAX(trade_date) as last_date,
    ROUND(AVG(close_price), 2) as avg_price
FROM market_data
GROUP BY symbol
ORDER BY symbol;
"
