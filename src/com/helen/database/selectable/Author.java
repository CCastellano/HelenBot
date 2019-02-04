package com.helen.database.selectable;

import com.helen.database.*;

import java.sql.ResultSet;
import java.sql.SQLException;

public class Author implements Selectable {
	
	public final String authorName;

	public Author(ResultSet rs) throws SQLException {
    this.authorName = rs.getString("created_by");
  }

	@Override
	public String getDisplay() {
		return authorName;
	}

	@Override
	public String run() throws SQLException {
		return Pages.getAuthorDetailsPages(authorName);
	}
}
