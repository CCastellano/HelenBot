package com.helen.database;

import com.helen.*;
import com.helen.commands.*;
import com.helen.error.*;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;

public final class Users {

  public static void insertUser(String username, String hostmask, String message, String channel)
      throws SQLException {
    try (
        Connection conn = Connector.getConnection();
        PreparedStatement insertUser = Connector.prepare(conn, "insertUser",
            "INSERT INTO users " +
            "(username, last_message, first_message, channel) " +
            "VALUES (?, ?, ?, ?) " +
            "ON CONFLICT (username, channel) DO UPDATE " +
            "SET last_seen = excluded.last_seen, last_message = excluded.last_message",
            username.toLowerCase(), message, message, channel
        );
        PreparedStatement insertHostmask = Connector.prepare(conn, "insertHostmask",
            "INSERT INTO hostmasks (username, hostmask) VALUES (?, ?) " +
            "ON CONFLICT (username, hostmask) DO UPDATE SET established = excluded.established",
            username.toLowerCase(), hostmask
        )
    ) {
      if (insertUser.executeUpdate() > 0) {
        insertHostmask.executeUpdate();
      }
    }
  }

  public static String seen(CommandData data) throws SQLException, IncorrectUsageException {
    if (data.channel == null) {
      throw new IncorrectUsageException(Command.CHANNEL_ONLY);
    }
    String channel = data.channel.toLowerCase();
    String target = data.getTarget();
    if ("-f".equals(target) && data.splitMessage.length >= 2) {
      target = data.splitMessage[2];
      try (
          Connection conn = Connector.getConnection();
          PreparedStatement stmt = Connector.prepare(conn, "seenFirst",
              "SELECT * FROM users WHERE USERNAME = ? AND CHANNEL = ? LIMIT 1",
              target.toLowerCase(), channel
          );
          ResultSet rs = stmt.executeQuery()
      ) {
        if (rs.next()) {
          Date date = rs.getDate("first_seen");
          String ago = LocalDate.now().equals(date.toLocalDate())
                       ? "today"
                       : Utils.findTime(date.getTime());

          return "I first met " + target + ' ' + ago + " saying: " + rs.getString("first_message");
        } else {
          return "I have never seen someone by that name.";
        }
      }
    } else if (target == null) {
      throw new IncorrectUsageException(Command.MORE_ARGS);
    } else {
      try (
          Connection conn = Connector.getConnection();
          PreparedStatement stmt = Connector.prepare(conn, "seen",
              "SELECT * FROM users WHERE USERNAME = ? AND CHANNEL = ? LIMIT 1",
              target.toLowerCase(), channel
          );
          ResultSet rs = stmt.executeQuery()
      ) {
        if (rs.next()) {
          return "I last saw " + target + ' ' +
                 Utils.findTime(rs.getTimestamp("last_seen").getTime()) + " saying: " +
                 rs.getString("last_message");
        } else {
          return "I have never seen someone by that name.";
        }
      }
    }
  }
}
