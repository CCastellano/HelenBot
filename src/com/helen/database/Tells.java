package com.helen.database;

import com.helen.commands.*;
import com.helen.error.*;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public final class Tells {
  public static String sendMultitell(CommandData data) throws SQLException, IncorrectUsageException {
    String sender = data.sender.toLowerCase();
    String target = data.getTarget();
    String message = data.getTellMessage();
    if (target == null) {
      throw new IncorrectUsageException("Please specify a target.");
    } else if (message == null) {
      throw new IncorrectUsageException("Please specify a message.");
    } else {
      int id = Nicks.getNickGroup(target);
      String tellTarget = id == -1 ? target.toLowerCase() : Integer.toString(id);
      return sendTell(tellTarget, sender, message, data.channel == null);
    }
  }

  public static String sendTell(String target, String sender, String message, boolean privateMessage) throws SQLException {
    try (
        Connection conn = Connector.getConnection();
        PreparedStatement stmt = Connector.prepare(conn, "insertTell",
            "INSERT INTO tells (username, sender, message, privatemessage) " +
            "VALUES (?, ?, ?, ?)",
            target.toLowerCase(), sender, message, privateMessage
        )
    ) {
      if (stmt.executeUpdate() > 0) {
        return "*Jots that down on her clipboard* Got it, I'll let them know…";
      } else {
        return Command.error();
      }
    }
  }

  public static List<Tell> getTells(String username) throws SQLException {
    /*
    possibly:
    WITH x as (SELECT * FROM nicks WHERE nick = ?)
    SELECT * FROM tells WHERE username = ?
    OR username IN (SELECT nicks.nick FROM nicks JOIN x ON x.id = nicks.id)
     */
    try (
        Connection conn = Connector.getConnection();
        PreparedStatement stmt = Connector.prepare(conn, "searchTells",
            "SELECT * FROM tells WHERE username = ?",
            username.toLowerCase()
        );
        ResultSet rs = stmt.executeQuery()
    ) {
      List<Tell> list = new ArrayList<>();
      while (rs.next()) {
        list.add(new Tell(username, rs));
      }
      return list;
    }
  }

  public static void clearTells(String username) throws SQLException {
    /*
    possibly:
    WITH x as (SELECT * FROM nicks WHERE nick = ?)
    DELETE FROM tells WHERE username = ?
    OR username IN (SELECT nicks.nick FROM nicks JOIN x ON x.id = nicks.id)
     */
    try (
        Connection conn = Connector.getConnection();
        PreparedStatement stmt = Connector.prepare(conn, "clearTells",
            "DELETE FROM tells WHERE username = ?",
            username.toLowerCase()
        )
    ) {
      stmt.executeUpdate();
    }
  }

}
