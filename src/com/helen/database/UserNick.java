package com.helen.database;

import com.helen.commands.*;
import org.apache.log4j.Logger;

import javax.annotation.Nullable;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class UserNick {
  private static final Logger logger = Logger.getLogger(UserNick.class);

  public final int groupId;
  public final boolean isNewNick;
  @Nullable public final String nickToGroup;

  public UserNick(CommandData data) throws SQLException {
    int id = Nicks.getNickGroup(data.sender);
    if (id != -1) {
      String target = data.getTarget();
      this.groupId     = id;
      this.isNewNick   = true;
      this.nickToGroup = target != null && !Nicks.getNicksByGroup(id).contains(target)
                         ? target
                         : null;
    } else {
      this.isNewNick = true;
      try (
          Connection conn = Connector.getConnection();
          PreparedStatement stmt = Connector.prepare(conn, "create_nick_group",
              "INSERT INTO nick_groups (id) VALUES (default) RETURNING id"
          );
          ResultSet newId = stmt.executeQuery()
      ) {
        this.groupId = newId.next() ? newId.getInt("id") : -1;
        this.nickToGroup = data.getTarget();

      }
    }
    logger.info("groupid " + groupId + ". NickToGroup: " + nickToGroup);
  }
}