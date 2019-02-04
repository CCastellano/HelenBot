package com.helen.background;

import com.helen.database.*;
import org.apache.log4j.Logger;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.net.URL;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class AttributionFetcher {
  private static final Logger logger = Logger.getLogger(AttributionFetcher.class);

  private static final String PAGE = "http://www.scp-wiki.net/attribution-metadata";
  private static final int TIMEOUT = 5_000; // Five seconds in milliseconds.

  // Parses the metadata page and batch-inserts attributions into the database.
  public static void update() {
    try (
        Connection conn = Connector.getConnection();
        PreparedStatement stmt = Connector.prepare(conn, "setAttributions",
            "INSERT INTO attributions (pageid, created_by, kind) " +
            "(SELECT pageid, ?, ? FROM pages WHERE pagename = ?) " +
            "ON CONFLICT DO NOTHING"
        )
    ) {
      for (Element tr : Jsoup.parse(new URL(PAGE), TIMEOUT).select(".wiki-content-table tr")) {
        Elements tds = tr.getElementsByTag("td");
        if (tds.size() == 4) {
          stmt.setString(1, tds.get(1).text().toLowerCase()); // User
          stmt.setString(2, tds.get(2).text()); // author, rewrite, translator, or maintainer
          stmt.setString(3, tds.get(0).text()); // pagename
          stmt.addBatch();
        }
      }
      stmt.executeBatch();
    } catch (IOException | SQLException e) {
      logger.error("Page fetcher error", e);
    }
  }
}
