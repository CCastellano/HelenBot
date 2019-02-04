package com.helen.database;

import com.helen.*;
import org.apache.commons.dbcp2.BasicDataSource;

import javax.annotation.Nullable;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public final class Connector {
  private static final DataSource pool = new BasicDataSource() {{
    this.setUrl(Utils.env("DB_URL"));
    this.setUsername(Utils.env("DB_USER"));
    this.setPassword(Utils.env("DB_PASSWORD"));
  }};

  public static Connection getConnection() throws SQLException {
    return pool.getConnection();
  }

  public static PreparedStatement prepare(Connection conn, @Nullable String query, String orDefault,
                                          Object...args) throws SQLException {
    PreparedStatement stmt = conn
        .prepareStatement(query == null ? orDefault : Statements.get(query, orDefault));
    for (int i = 0; i < args.length; i++) {
      stmt.setObject(i + 1, args[i]);
    }
    return stmt;
  }
}
