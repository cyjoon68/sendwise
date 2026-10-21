package com.sendwise.api.infra.balance;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public class JdbcAccounts {
    static final String DEBIT = "UPDATE balance SET amount = amount - :cost WHERE client_id = :id AND amount >= :cost";

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcAccounts(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean clientExists(long clientId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM client WHERE id = :id",
                new MapSqlParameterSource("id", clientId),
                Integer.class
        );
        return count != null && count > 0;
    }

    public boolean debitIfEnough(long clientId, long cost) {
        int rows = jdbc.update(DEBIT, new MapSqlParameterSource().addValue("cost", cost).addValue("id", clientId));
        return rows == 1;
    }

    public void credit(long clientId, long amount) {
        jdbc.update(
                "UPDATE balance SET amount = amount + :amount WHERE client_id = :id",
                new MapSqlParameterSource().addValue("amount", amount).addValue("id", clientId)
        );
    }

    public long amount(long clientId) {
        List<Long> rows = jdbc.query(
                "SELECT amount FROM balance WHERE client_id = :id",
                new MapSqlParameterSource("id", clientId),
                (rs, row) -> rs.getLong(1)
        );
        return rows.isEmpty() ? 0 : rows.get(0);
    }

    public void ledger(long clientId, Long campaignId, Long messageId, String entryType, long amount, Instant at) {
        jdbc.update(
                """
                INSERT INTO ledger (client_id, campaign_id, message_id, entry_type, amount, created_at)
                VALUES (:clientId, :campaignId, :messageId, :entryType, :amount, :createdAt)
                """,
                new MapSqlParameterSource()
                        .addValue("clientId", clientId)
                        .addValue("campaignId", campaignId)
                        .addValue("messageId", messageId)
                        .addValue("entryType", entryType)
                        .addValue("amount", amount)
                        .addValue("createdAt", java.sql.Timestamp.from(at))
        );
    }
}
