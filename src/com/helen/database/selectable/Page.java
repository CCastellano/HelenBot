package com.helen.database.selectable;

import com.helen.*;
import com.helen.database.*;
import org.apache.xmlrpc.XmlRpcException;

import javax.annotation.Nullable;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Map;

public class Page implements Selectable {

	private static String title(String title, @Nullable String scpTitle) {
		return scpTitle == null ? title : title + ": " + scpTitle;
	}

	public final String pageName;
	public final String title;
	public final int rating;
  @Nullable public final String createdBy;
  public final Timestamp createdOn;

  public Page(ResultSet rs) throws SQLException {
    this.pageName  = rs.getString("pagename");
    this.title     = title(rs.getString("title"), rs.getString("scptitle"));
    this.rating    = rs.getInt("rating");
    this.createdBy = rs.getString("created_by");
    this.createdOn = rs.getTimestamp("created_on");
  }

  public Page(Map<String, Object> rpc) {
	  this.pageName  = (String)  rpc.get("fullname");
	  this.title     = (String)  rpc.get("title");
	  this.rating    = (Integer) rpc.get("rating");
    this.createdBy = (String)  rpc.get("created_by");
    this.createdOn = new Timestamp(Utils.parseZonedTime((String) rpc.get("created_at")));
  }

	@Override
	public String getDisplay() {
		return title;
	}

	@Override
	public String run() throws XmlRpcException, SQLException {
		return Pages.getPageInfo(pageName);
	}

	@Override
	public int hashCode() {
		return pageName.hashCode();
	}

	@Override
	public boolean equals(Object o) {
		return o instanceof Page && ((Page) o).pageName.equals(pageName);
	}
}
