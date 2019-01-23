package com.helen.processes;

import com.helen.database.Connector;
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

  private static final String URL  = "http://www.scp-wiki.net/attribution-metadata";
  private static final long NANO   = 1000000; // One millisecond in nanoseconds.
  private static final long SLEEP  = 3600000; // One hour in milliseconds.
  private static final String STMT = "INSERT INTO attributions (pageid, created_by, kind) " +
                                     "(SELECT pageid, ?, ? FROM pages WHERE pagename = ?) " +
                                     "ON CONFLICT DO NOTHING";
  // Event loop.
  public static void run() {
    logger.info("Starting attribution fetcher.");
    while (true) {
      try {
        final long start = System.nanoTime();
        update();
        final long finish = System.nanoTime();
        final long elapsed = (finish - start) / NANO;
        logger.info("Scanned metadata in " + elapsed + "ms.");
        Thread.sleep(SLEEP);
      } catch (final InterruptedException e) {
        logger.error(e);
        return;
      } catch (final Exception e) {
        logger.error(e);
      }
    }
  }

  // Parses the metadata page and batch-inserts attributions into the database.
  private static void update() throws IOException, SQLException {
    try (final Connection conn = Connector.getConnection()) {
      final PreparedStatement ps = conn.prepareStatement(STMT);

      for (final Element el : Jsoup.parse(new URL(URL), 3000).select(".wiki-content-table tr")) {
        final Elements els = el.getElementsByTag("td");
        if (els.size() == 4) {
          ps.setString(1, els.get(1).text().toLowerCase()); // User
          ps.setString(2, els.get(2).text()); // author, rewrite, translator, or maintainer
          ps.setString(3, els.get(0).text()); // pagename
          ps.addBatch();
        }
      }
      ps.executeBatch();
    }
  }
}
