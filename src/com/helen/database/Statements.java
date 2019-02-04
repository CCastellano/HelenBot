package com.helen.database;

import org.apache.log4j.Logger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

public final class Statements {
	private static final Logger logger = Logger.getLogger(Statements.class);

  private static final Map<String, String> statementCache = new HashMap<>();
	private static boolean valid = false;

	public static void clear() {
		valid = false;
		loadStatements();
	}

	public static String get(String name, String ifNull) {
		if (!valid) {
			loadStatements();
		}

		return statementCache.getOrDefault(name, ifNull);
	}

	private static void loadStatements() {
		try (
		    Connection conn = Connector.getConnection();
		    PreparedStatement stmt = conn.prepareStatement("SELECT * FROM statements");
		    ResultSet rs = stmt.executeQuery()
    ) {
			while (rs.next()) {
				statementCache.put(rs.getString("name"), rs.getString("statement"));
			}
			valid = true;
		} catch (SQLException e) {
			logger.error("Exception trying to load statements in to cache", e);
		}
	}

}
