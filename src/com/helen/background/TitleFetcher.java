package com.helen.background;

import com.helen.database.*;
import org.apache.log4j.Logger;
import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.net.URL;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class TitleFetcher {
  private static final Logger logger = Logger.getLogger(TitleFetcher.class);

  private static final int TIMEOUT = 5_000; // Five seconds in milliseconds.

  private static boolean parsePage(PreparedStatement ps, String url) throws SQLException {
    Document doc;
    try {
      doc = Jsoup.parse(new URL(url), TIMEOUT);
    } catch (HttpStatusException ignored) {
      return false;
    } catch (IOException e) {
      logger.error("Page fetcher error for " + url, e);
      return false;
    }
    Elements series = doc.getElementsByClass("series");
    if (series.isEmpty()) {
      return false;
    }
    for (Element el : series.first().getElementsByTag("li")) {
      String title = el.text();
      int dash = title.indexOf("- ");
      if (dash != -1) {
        title = title.substring(dash + 2);
      }
      String href = null;
      Elements links = el.getElementsByTag("a");
      if (!links.isEmpty()) {
        href = links.first().attr("href").substring(1);
      }
      if (href != null && !href.isEmpty()) {
        if (title.startsWith("- ")) {
          title = title.substring(2);
        }
        ps.setString(1, title.isEmpty() || "[ACCESS DENIED]".equals(title) ? null : title);
        ps.setString(2, href.toLowerCase());
        ps.addBatch();
      }
    }
    return true;
  }

  // Parses the metadata page and batch-inserts attributions into the database.
  public static void update() {
    try (
        Connection conn = Connector.getConnection();
        PreparedStatement ps = Connector.prepare(conn, "setTitles",
            "UPDATE pages SET scptitle = ? WHERE pagename = ?"
        )
    ) {
      parsePage(ps, "http://www.scp-wiki.net/joke-spcs");
      parsePage(ps, "http://www.scp-wiki.net/scp-series");
      for (int i = 2; parsePage(ps, "http://www.scp-wiki.net/scp-series-" + i); i++);
      ps.executeBatch();
    } catch (SQLException e) {
      logger.error("Title fetcher error", e);
    }
  }
}
