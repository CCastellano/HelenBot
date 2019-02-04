package com.helen.database;

import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;

public class TestBans {
  @Test
  public void updateBans() throws IOException {
    Bans.updateBans();
    Assert.assertNotEquals("No bans found", 0, Bans.getBans().size());
  }
}
