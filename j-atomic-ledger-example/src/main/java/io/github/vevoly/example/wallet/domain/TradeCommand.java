package io.github.vevoly.example.wallet.domain;

import io.github.vevoly.ledger.api.BaseCommand;
import lombok.Data;

@Data
public class TradeCommand extends BaseCommand {
    private Long userId;
    private long amount;
}
