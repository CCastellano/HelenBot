package com.helen.database;

import java.sql.ResultSet;
import java.sql.SQLException;

public class Config {
	
	public final String key;
	public final String value;
	public final boolean isPublic;

	public Config(String key, String value, boolean isPublic) {
		this.key      = key;
		this.value    = value;
		this.isPublic = isPublic;
	}

	public Config(ResultSet rs) throws SQLException {
    this.key      = rs.getString("key");
    this.value    = rs.getString("value");
    this.isPublic = rs.getBoolean("public");
  }

	@Override
	public String toString() {
		return key + ": " + value;
	}
}
