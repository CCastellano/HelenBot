package com.helen.database;

import org.jibble.pircbot.Colors;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class Roll {
  private static final int TRUNCATE = 20;

  final int diceSize;
  private final char diceType;
  private final String username;
  private final List<Integer> values;
  private int amount;
  private int total;

  private final boolean positive;
  boolean expand;

  Roll(char diceType, int diceSize, String username, List<Integer> values, boolean positive) {
    this.diceType = diceType;
    this.diceSize = diceSize;
    this.username = username;
    this.values   = values;
    this.positive = positive;
    this.expand   = false;
    this.amount   = values.size();
    this.total    = values.stream().mapToInt(Integer::intValue).sum();
  }

  Roll(ResultSet rs) throws SQLException {
    this.diceType = rs.getString("type").charAt(0);
    this.diceSize = rs.getInt("size");
    this.username = rs.getString("username");
    this.values   = new ArrayList<>();
    this.total    = rs.getInt("total");
    this.positive = true;
    this.expand   = true;
    this.amount   = 0;
  }

  public void addRoll(int i) {
    values.add(i);
    amount++;
    total += i;
  }

  private int getScore() {
    return positive ? total : -total;
  }

  public boolean insert() throws SQLException {
    try (
        Connection conn = Connector.getConnection();
        PreparedStatement insertRolls = Connector.prepare(conn, "insertRolls",
            "INSERT INTO rolls (username, amount, type, size, total) VALUES (?, ?, ?, ?, ?) " +
            "RETURNING rollid",
            username.toLowerCase(), 0, diceType, diceSize, total
        );
        PreparedStatement insertRoll = Connector.prepare(conn, "insertRoll",
                "INSERT INTO roll (rollid, value) VALUES (?, ?)"
        );
        ResultSet rs = insertRolls.executeQuery()
    ) {
      if (rs.next()) {
        int rollId = rs.getInt("rollID");
        insertRolls.close();
        for (int i = 0; i < TRUNCATE + 1 && i < values.size(); i++) {
          insertRoll.setInt(1, rollId);
          insertRoll.setInt(2, values.get(i));
          insertRoll.addBatch();
        }
        insertRoll.executeBatch();
        return true;
      }
      return false;
    }
  }

  @Override
  public String toString() {
    StringBuilder str = new StringBuilder();
    if (!positive) {
      str.append('-');
    }
    str .append(amount)
        .append('d')
        .append(diceType == 'f' ? "F" : diceSize)
        .append('=')
        .append(Colors.BOLD)
        .append(getScore())
        .append(Colors.NORMAL);

    if (expand) {
      str.append(": [");
      for (int i = 0; i < amount && i < TRUNCATE; i++) {
        int val = values.get(i);
        if (i > 0 && (val != 0 || diceType == 'd')) {
          str.append(' ');
        }
        if (diceType != 'f') {
          str.append(values.get(i));
        } else if (val < 0) {
          str.append('-');
        } else if (val > 0) {
          str.append('+');
        }
      }
      if (amount > TRUNCATE) {
        str.append('…');
      }
      str.append(']');
    }
    return str.toString();
  }
}
