package com.helen.database;

import com.helen.commands.*;
import com.helen.error.*;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class Pronouns {

  private static final Pattern DELIMS = Pattern.compile("['/\\\\, ]");
  private static final Collection<String> bannedNouns = new ArrayList<>();

  static {
    reload();
  }

  public static String getPronouns(String user) throws SQLException {
    try (
        Connection conn = Connector.getConnection();
        PreparedStatement stmt = Connector.prepare(conn, "getPronouns",
            "SELECT * FROM pronouns JOIN pronoun ON pronouns.pronounid = pronoun.pronounid " +
            "WHERE username = ?",
            user.toLowerCase()
        );
        ResultSet rs = stmt.executeQuery()
    ) {
      StringBuilder str = new StringBuilder();
      StringBuilder accepted = new StringBuilder();
      StringBuilder pronouns = new StringBuilder();
      while (rs.next()) {
        if (rs.getBoolean("accepted")) {
          if (accepted.length() > 0) {
            accepted.append(", ");
          }
          accepted.append(rs.getString("pronoun"));
        } else {
          if (pronouns.length() > 0) {
            pronouns.append(", ");
          }
          pronouns.append(rs.getString("pronoun"));
        }
      }
      if (accepted.length() > 0 || pronouns.length() > 0) {
        if (pronouns.length() > 0) {
          str .append(user)
              .append(" uses the following pronouns: ")
              .append(pronouns.toString())
              .append(';');
        } else {
          str.append(" I have no record of pronouns;");
        }
        if (accepted.length() > 0) {
          str .append(' ')
              .append(user)
              .append(" accepts the following pronouns: ")
              .append(accepted.toString());
        } else {
          str.append(" I have no record of accepted pronouns");
        }
        str.append('.');
      } else {
        str .append("I'm sorry, I don't have any record of pronouns for ")
            .append(user);
      }
      return str.toString();
    }
  }

  public static String insertPronouns(CommandData data) throws IncorrectUsageException, SQLException {
    String nounData = data.getMessageWithoutCommand();
    if (nounData == null) {
      throw new IncorrectUsageException(
          "Usage: .setPronouns (accepted) pronoun1 pronoun2 pronoun3 … pronoun[n]"
      );
    }
    boolean accepted = nounData.startsWith("accepted");
    if (accepted) {
      nounData = nounData.substring(8);
    }
    String[] nouns = DELIMS
        .splitAsStream(nounData)
        .filter(x -> !x.isEmpty())
        .toArray(String[]::new);
    if (nouns.length == 0) {
      throw new IncorrectUsageException(
          "Usage: .setPronouns (accepted) pronoun1 pronoun2 pronoun3 … pronoun[n]"
      );
    }
    Collection<String> banned = Arrays
        .stream(nouns)
        .filter(x -> bannedNouns.contains(x.toLowerCase()))
        .collect(Collectors.toSet());
    if (!banned.isEmpty()) {
      return "Your pronoun list contains " +
             (banned.size() > 1 ? "banned terms" : "a banned term") +
             ": " + String.join(", ", banned);
    }
    try (
        Connection conn = Connector.getConnection();
        PreparedStatement insertPronouns = Connector.prepare(conn, "establishPronoun",
            "INSERT INTO pronouns (username, accepted) VALUES (?, ?) RETURNING pronounid",
            data.sender.toLowerCase(), accepted
        );
        PreparedStatement insertPronoun = Connector.prepare(conn, "insertPronoun",
            "INSERT INTO pronoun (pronounid, pronoun) VALUES (?, ?)"
        );
        ResultSet rs = insertPronouns.executeQuery()
    ) {
      if (nouns.length > 0 && rs.next()) {
        int pronounID = rs.getInt("pronounID");
        int j = 0;
        if ("accepted".equalsIgnoreCase(nouns[0])) {
          j = 1;
        }

        for (int i = j; i < nouns.length; i++) {
          insertPronoun.setInt(1, pronounID);
          insertPronoun.setString(2, nouns[i]);
          insertPronoun.addBatch();
        }
        insertPronoun.executeBatch();
      }
      String pronoun = nouns.length == 1 ? "pronoun" : "pronouns";
      return "Inserted the following " + pronoun + ": " + String.join(", ", nouns) + " as" +
             (accepted ? " accepted " : " ") + pronoun + '.';
    }
  }

  public static String clearPronouns(String username) throws SQLException {
    try (
        Connection conn = Connector.getConnection();
        PreparedStatement deleteNouns = Connector.prepare(conn, "deleteNouns",
            "DELETE FROM pronoun WHERE pronounid IN " +
            "(SELECT pronounid FROM pronouns WHERE username = ?)",
            username.toLowerCase()
        );
        PreparedStatement deleteNounRecord = Connector.prepare(conn, "deleteNounRecord",
            "DELETE FROM pronouns WHERE username = ?",
            username.toLowerCase()
        )
    ) {
      deleteNouns.executeUpdate();
      deleteNounRecord.executeUpdate();
      return "Deleted all pronoun records for " + username + '.';
    }
  }

  public static void reload() {
    bannedNouns.clear();
    // Just a couple examples.
    bannedNouns.add("apache");
    bannedNouns.add("helicopter");
    // More are added on the back end.
    for (Config c : Configs.getProperty("bannedNouns")) {
      bannedNouns.add(c.value);
    }
  }

}
