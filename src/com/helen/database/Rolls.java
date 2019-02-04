package com.helen.database;

import com.helen.*;
import com.helen.error.*;
import org.jibble.pircbot.Colors;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.regex.Pattern;

public final class Rolls {

  private static final int AMOUNT_LIMIT = 5_000;
  private static final int AVG_DECIMALS = 2;
  private static final double AVG_POW   = StrictMath.pow(10, AVG_DECIMALS);
  private static final NumberFormat FMT = NumberFormat.getNumberInstance(Locale.US);
  private static final Pattern SIGNUM   = Pattern.compile("\\s*([+-])\\s*");

  private static final Random rng = new Random();

  public static String roll(String cmd, String usr) throws IncorrectUsageException, SQLException {
    Collection<Roll> rolls = new ArrayList<>();
    int score = 0;
    String[] words = Utils.split(' ', SIGNUM.matcher(cmd).replaceAll(" $1"));
    int i = 0;
    for (; i < words.length; i++) {
      try {
        String word = words[i];
        String[] info = Utils.split('d', word);
        switch (info.length) {
          case 0:
            throw new IncorrectUsageException("I'm sorry, that isn't a valid dice string.");

          case 1:
            score += Integer.parseInt(word);
            break;

          case 2:
            int amount = Integer.parseInt(info[0]);
            int signum = Integer.signum(amount);
            amount = StrictMath.abs(amount);
            if (amount > AMOUNT_LIMIT) {
              throw new IncorrectUsageException(
                  "Cannot roll more than " + AMOUNT_LIMIT + " dice at a time."
              );
            }
            char diceType = 'd';
            int min, max;
            switch (info[1]) {
              case "f":
              case "F":
                diceType = 'f';
                min = -1;
                max = 1;
                break;

              case "%":
                min = 1;
                max = 100;
                break;

              default:
                min = 1;
                max = Integer.parseInt(info[1]);
            }
            if (max < min) {
              throw new IncorrectUsageException(max + " is too few dice sides.");
            }
            int sides = max - min + 1;
            List<Integer> values = new ArrayList<>(amount);
            for (int j = 0; j < amount; j++) {
              int result = rng.nextInt(sides) + min;
              score += signum * result;
              values.add(result);
            }
            rolls.add(new Roll(diceType, sides, usr, values, signum >= 0));
            break;

          default:
            break;
        }
      } catch (NumberFormatException e) {
        break;
      }
    }
    if (i == 0) {
      throw new IncorrectUsageException("I'm sorry, that isn't a valid dice string.");
    }
    boolean expand = false;
    StringBuilder str = new StringBuilder();
    boolean hasMessage = i < words.length;
    for (boolean space = false; i < words.length; i++) {
      String word = words[i];
      if ("-e".equals(word)) {
        expand = true;
      } else {
        if (space) {
          str.append(' ');
        }
        space = true;
        str.append(words[i]);
      }
    }

    if (hasMessage) {
      str.append(": ");
    }
    str .append(Colors.BOLD)
        .append(FMT.format(score))
        .append(Colors.NORMAL);

    if (!rolls.isEmpty()) {
      str.append(" (");
      boolean comma = false;
      for (Roll roll : rolls) {
        if (expand) {
          roll.expand = true;
        }
        if (comma) {
          str.append(", ");
        }
        comma = true;

        str.append(roll);
        roll.insert();
      }
      str.append(')');
    }

    return str.toString();
  }

  public static String getAverage(String size, String username)
      throws IncorrectUsageException, SQLException {
    int diceSize;
    try {
      diceSize = Integer.parseInt(size);
    } catch (NumberFormatException e) {
      throw new IncorrectUsageException(size + " is not a valid integer.", e);
    }
    try (
        Connection conn = Connector.getConnection();
        PreparedStatement stmt = Connector.prepare(conn, "average",
            "SELECT avg(value) AS average FROM roll JOIN rolls ON roll.rollid = rolls.rollid " +
            "WHERE size = ? AND username = ?",
            diceSize, username.toLowerCase()
        );
        ResultSet rs = stmt.executeQuery()
    ) {
      if (rs.next()) {
        return "The average roll for a d" + diceSize + " for you is: " + Colors.BOLD +
               StrictMath.round(rs.getDouble("average") * AVG_POW) / AVG_POW + Colors.NORMAL + '.';
      } else {
        return "I didn't find any rolls of that size in your history.";
      }
    }
  }

  public static Collection<Roll> getRolls(String username) throws SQLException {
    try (
        Connection conn = Connector.getConnection();
        PreparedStatement stmt = Connector.prepare(conn, "getRolls",
            "SELECT * FROM roll JOIN rolls ON roll.rollid = rolls.rollid " +
            "WHERE username=? AND username=?",
            username.toLowerCase(), username.toLowerCase()
        );
        ResultSet rs = stmt.executeQuery()
    ) {

      Map<Integer, Roll> rollMap = new HashMap<>();
      while (rs.next()) {
        int key = rs.getInt("rollId");
        Roll roll = rollMap.get(key);
        if (roll == null) {
          roll = new Roll(rs);
          rollMap.put(key, roll);
        }
        roll.addRoll(rs.getInt("value"));
      }
      return rollMap.values();
    }
  }

}
