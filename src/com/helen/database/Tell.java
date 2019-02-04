package com.helen.database;

import javax.annotation.Nullable;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;

public class Tell {

  private static final SimpleDateFormat FMT = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss");

  private final String sender;
  private final Timestamp tell_time;
  private final String message;
  public final boolean isPrivate;
  public final String target;
  @Nullable public final Integer nickGroupId;

  public Tell(String target, ResultSet rs) throws SQLException {
    this.sender      = rs.getString("sender");
    this.target      = target;
    this.tell_time   = rs.getTimestamp("tell_time");
    this.message     = rs.getString("message");
    this.isPrivate   = rs.getBoolean("privateMessage");
    this.nickGroupId = null;
  }

  public String toString() {
    return target + ": " + sender + " said at " + FMT.format(tell_time) + ": " + message;
  }
}
