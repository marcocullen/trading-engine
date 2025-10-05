docker exec -it trading-postgres psql -U trading_user -d trading_db -c "
SELECT symbol, COUNT(*) as days, MIN(trade_date) as first_date, MAX(trade_date) as last_date
FROM market_data
GROUP BY symbol
ORDER BY symbol;
"