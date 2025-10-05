docker exec -it trading-postgres psql -U trading_user -d trading_db -c "
SELECT
    indicator_name,
    COUNT(DISTINCT symbol) as stocks_covered,
    COUNT(*) as total_calculations,
    MIN(trade_date) as first_calc,
    MAX(trade_date) as last_calc
FROM technical_indicators
GROUP BY indicator_name
ORDER BY indicator_name;
"
