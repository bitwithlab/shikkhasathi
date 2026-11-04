package com.shikkhasathi.common.testing;

/** Fake adapters that keep in-memory state (outboxes, sent pushes) implement this; tests reset them before each case. */
public interface ResettableFake {
  void reset();
}
