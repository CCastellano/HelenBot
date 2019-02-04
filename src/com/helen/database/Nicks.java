package com.helen.database;

import com.helen.commands.*;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public final class Nicks {

  public static String addNick(CommandData data) throws SQLException {
    //int id = getNickGroup(data.sender);
    //if (id != -1) {
    UserNick nick = new UserNick(data);
    if (nick.nickToGroup == null) {
      return "Your nick is already grouped with ID: " + nick.groupId;
    } else {
      try (
          Connection conn = Connector.getConnection();
          PreparedStatement insertStatement = Connector.prepare(conn, "insert_grouped_nick",
              "INSERT INTO nicks (id, nick) VALUES (?, ?)",
              nick.groupId, nick.nickToGroup.toLowerCase()
          )
      ) {
        if (insertStatement.executeUpdate() == 0) {
          return Command.error();
        } else if (nick.isNewNick) {
          return "Established a new nickgroup under " + data.sender +
                 " and added the nick " + nick.nickToGroup + " as a grouped nick.";
        } else {
          return "Inserted " + nick.nickToGroup + " for user " + data.sender + '.';
        }
      }
    }
  }

  public static String deleteNick(CommandData data) throws SQLException {
    int id = getNickGroup(data.sender);
    String target = data.getTarget();
    if (id != -1 && target != null) {
      try (
          Connection conn = Connector.getConnection();
          PreparedStatement stmt = Connector.prepare(conn, "deleteGroupedNick",
              "DELETE FROM nicks WHERE nick = ?",
              target.toLowerCase()
          )
      ) {
        stmt.executeUpdate();
      }
      return "Deleted " + data.getTarget() + " from your grouped nicks.";
    } else {
      return "I didn't find any grouped nicks for your username.";
    }
  }

  private static boolean deleteNick(String data) throws SQLException {
    try (
        Connection conn = Connector.getConnection();
        PreparedStatement stmt = Connector.prepare(conn, "deleteGroupedNick",
            "DELETE FROM nicks WHERE nick = ?",
            data.toLowerCase()
        )
    ) {
      return stmt.executeUpdate() > 0;
    }
  }

  public static String deleteAllNicks(CommandData data, boolean admin) throws SQLException {
    String target = data.getTarget();
    int id;
    if (!admin) {
      id = getNickGroup(data.sender);
    } else if (target == null) {
      id = -1;
    } else {
      id = getNickGroup(target);
    }
    if (id != -1) {
      for (String nick : getNicksByGroup(id)) {
        if (!deleteNick(nick)) {
          return "There was a problem deleting nicks.";
        }
      }
      return "Deleted all nicks for your group.";
    } else {
      return "Your nick is not grouped";
    }
  }

  public static List<String> getNicksByGroup(int id) throws SQLException {
    try (
        Connection conn = Connector.getConnection();
        PreparedStatement stmt = Connector.prepare(conn, "getNick",
            "SELECT nick FROM nicks WHERE id = ?",
            id
        );
        ResultSet rs = stmt.executeQuery()
    ) {
      List<String> nicks = new ArrayList<>();
      while (rs.next()) {
        nicks.add(rs.getString("nick"));
      }
      return nicks;
    }
  }

  public static int getNickGroup(String username) throws SQLException {
    try (
        Connection conn = Connector.getConnection();
        PreparedStatement stmt = Connector.prepare(conn, "find_nick_group",
            "SELECT id FROM nicks WHERE nick = ?",
            username.toLowerCase()
        );
        ResultSet rs = stmt.executeQuery()
    ) {
      if (rs.next()) {
        return rs.getInt("id");
      }
    }
    return -1;
  }


}
