package io.github.vevoly.example.wallet.domain;

import io.github.vevoly.ledger.api.BaseCommand;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class TradeCommand extends BaseCommand {
    private Long userId;
    private BigDecimal amount;

    @Override
    public String getRoutingKey() {
        return String.valueOf(userId);
    }
}
