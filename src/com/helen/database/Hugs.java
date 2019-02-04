package com.helen.database;

import com.helen.commands.*;
import com.helen.error.*;
import org.apache.log4j.Logger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public final class Hugs {
  private static final Logger logger = Logger.getLogger(Hugs.class);

  public static String getHugMessage(String username) {
    String hug = "I'm sorry, I don't think you've told me what you would like me to say yet.";
    try (
        Connection conn = Connector.getConnection();
        PreparedStatement stmt = Connector.prepare(conn, "getHug",
            "SELECT hug FROM hugs WHERE username = ?",
            username
        );
        ResultSet rs = stmt.executeQuery()
    ) {
      if (rs.next()) {
        hug = rs.getString("hug");
      }
    } catch(SQLException e) {
      logger.error("Couldn't get hug", e);
    }

    return hug;
  }

  public static String storeHugmessage(CommandData data) throws IncorrectUsageException {
    String msg = data.getMessageWithoutCommand();
    if (msg == null) {
      throw new IncorrectUsageException(Command.MORE_ARGS);
    }
    try (
        Connection conn = Connector.getConnection();
        PreparedStatement stmt = Connector.prepare(conn, "insertHug",
            "INSERT INTO hugs (username, hug) VALUES (?, ?) " +
            "ON CONFLICT (username) DO UPDATE SET hug = excluded.hug",
            data.sender.toLowerCase(), msg
        )
    ) {
      stmt.executeUpdate();
    } catch (SQLException e) {
      if (!e.getMessage().contains("hugs_pkey")) {
        logger.error("Couldn't store the hug", e);
      } else {
        logger.info("Updating");
        updateHugMessage(data.sender.toLowerCase(), msg);
      }
    }
    return "*Jots that down on her clipboard* Noted, " + data.sender + '.';
  }

  public static void updateHugMessage(String username, String message) {
    try (
        Connection conn = Connector.getConnection();
        PreparedStatement stmt = Connector.prepare(conn, "updateHug",
            "UPDATE public.hugs SET hug = ? WHERE username = ?",
            message, username
        )
    ) {
      stmt.executeUpdate();
      logger.info("Update finished");
    } catch(SQLException e) {
      logger.error("Couldn't store the hug", e);
    }
  }

}
