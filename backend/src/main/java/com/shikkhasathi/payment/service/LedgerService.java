package com.shikkhasathi.payment.service;

import com.shikkhasathi.common.Cursor;
import com.shikkhasathi.config.JsonConfig;
import com.shikkhasathi.integration.service.IntegrationService;
import com.shikkhasathi.payment.dto.CommerceDtos.Balance;
import com.shikkhasathi.payment.dto.CommerceDtos.LedgerEntry;
import com.shikkhasathi.payment.dto.CommerceDtos.LedgerPage;
import com.shikkhasathi.payment.repository.LedgerRepository;
import com.shikkhasathi.payment.repository.LedgerRepository.NewEntry;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Ledger-based seller earnings: append (idempotent by dedupe key), balance and history. */
@Service
public class LedgerService {
  private final LedgerRepository repo;
  private final IntegrationService integrations;

  public LedgerService(LedgerRepository repo, IntegrationService integrations) {
    this.repo = repo;
    this.integrations = integrations;
  }

  /** Append one entry; a repeat with the same dedupe key is a no-op. Serialized per seller so balance_after is consistent. */
  @Transactional
  public void append(UUID sellerId, NewEntry e) {
    repo.lockSeller(sellerId);
    if (repo.exists(e.dedupeKey())) return;
    repo.insert(sellerId, e, repo.lastBalance(sellerId) + e.amount());
  }

  public Balance balance(UUID sellerId) {
    return balances(List.of(sellerId)).get(sellerId);
  }

  /** Balances for several sellers in two queries. */
  public Map<UUID, Balance> balances(Collection<UUID> sellerIds) {
    var ids = new ArrayList<>(new LinkedHashSet<>(sellerIds));
    Map<UUID, Balance> out = new HashMap<>();
    if (ids.isEmpty()) return out;
    var sums = repo.sums(ids, Instant.now());
    var pending = repo.pendingPayouts(ids);
    long minPayout = Math.round(integrations.businessNumber("min_payout_bdt", 500) * 100);
    for (UUID id : ids) {
      var s = sums.get(id);
      long p = pending.get(id);
      out.put(id, new Balance(Math.max(0, s.available() - p), s.held(), s.lifetime(), s.paidOut(), p, minPayout));
    }
    return out;
  }

  public LedgerPage ledger(UUID sellerId, String cursor, int limit) {
    var cur = Cursor.decode(cursor);
    var rows = repo.page(sellerId, cur == null ? null : Instant.parse(cur.sortString()), limit + 1);
    var pg = Cursor.page(rows, limit, r -> new Cursor.Value(JsonConfig.iso(r.createdAt()), r.id().toString()));
    var items = pg.items().stream()
        .map(e -> new LedgerEntry(e.id(), e.type(), e.amount(), e.description(), JsonConfig.iso(e.availableAt()), e.balanceAfter(), JsonConfig.iso(e.createdAt())))
        .toList();
    return new LedgerPage(items, pg.nextCursor(), balance(sellerId));
  }
}
