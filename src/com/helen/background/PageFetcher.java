package com.helen.background;

import com.helen.*;
import com.helen.database.*;
import org.apache.log4j.Logger;
import org.apache.xmlrpc.XmlRpcException;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;

public class PageFetcher {
  private static final Logger logger = Logger.getLogger(PageFetcher.class);

  private static final int BATCH = 2_500; // Number of pages to add at a time.

  // Parses the metadata page and batch-inserts attributions into the database.
  public static void update() {
    Timestamp now = new Timestamp(System.currentTimeMillis());
    try (
        Connection conn = Connector.getConnection();
        PreparedStatement insertPage = Connector.prepare(conn, "insertPage",
            "INSERT INTO pages (pagename, title, rating, created_by, created_on, updatetime) " +
            "VALUES (?, ?, ?, ?, ?, ?) ON CONFLICT (pagename) " +
            "DO UPDATE SET title = excluded.title, rating = excluded.rating, " +
            "created_by = excluded.created_by, created_on = excluded.created_on, " +
            "updatetime = excluded.updatetime"
        );
        PreparedStatement insertTag = Connector.prepare(conn, "insertTag",
            "WITH page AS (SELECT pageid FROM pages WHERE pagename = ?), " +
            "new(tag) AS (VALUES (?)), " +
            "ins AS (INSERT INTO tags (tag) SELECT * FROM new " +
            "ON CONFLICT (tag) DO NOTHING RETURNING tagid), tagged as " +
            "(SELECT tagid FROM ins UNION ALL SELECT c.tagid FROM new JOIN tags c USING (tag)) " +
            "INSERT INTO pagetags (pageid, tagid, updatetime) " +
            "SELECT page.pageid, tagged.tagid, ? FROM page, tagged " +
            "ON CONFLICT (pageid, tagid) DO UPDATE SET updatetime = excluded.updatetime"
        );
        PreparedStatement deleteOldPages = Connector.prepare(conn, "deleteOldPages",
            "DELETE FROM pages WHERE updatetime < ?",
            now
        );
        PreparedStatement deleteOldPagetags = Connector.prepare(conn, "deleteOldPagetags",
            "DELETE FROM pagetags WHERE updatetime < ?",
            now
        );
        PreparedStatement deleteOldAttributions = Connector.prepare(conn, "deleteOldAttributions",
            "DELETE FROM attributions WHERE pageid NOT IN (select pageid from pages)"
        );
        PreparedStatement deleteOldTags = Connector.prepare(conn, "deleteOldtags",
            "DELETE FROM tags WHERE tagid NOT IN (select tagid from pagetags)"
        );
        PreparedStatement cleanTags = Connector.prepare(conn, "cleanTags",
            "SELECT setval('tags_tagid_seq', MAX(tagid)) FROM tags"
        );
    ) {
      for (String[] pages : Utils.chunk(BATCH, Pages.listPages())) {
        Pages.walk(pages, (page, tags) -> {
          try {
            insertPage.setString(1, page.pageName);
            insertPage.setString(2, page.title);
            insertPage.setInt(3, page.rating);
            insertPage.setString(4, page.createdBy);
            insertPage.setTimestamp(5, page.createdOn);
            insertPage.setTimestamp(6, now);
            insertPage.addBatch();

            for (String tag : tags) {
              insertTag.setString(1, page.pageName);
              insertTag.setString(2, tag);
              insertTag.setTimestamp(3, now);
              insertTag.addBatch();
            }
          } catch (SQLException e) {
            logger.error("Error inserting pages", e);
          }
        });
        insertPage.executeBatch();
        insertTag.executeBatch();
        insertPage.clearBatch();
        insertTag.clearBatch();
        deleteOldPages.executeUpdate();
        deleteOldAttributions.executeUpdate();
        deleteOldPagetags.executeUpdate();
        deleteOldTags.executeUpdate();
        cleanTags.execute();
      }
    } catch (XmlRpcException |SQLException e) {
      logger.error("Page fetcher error", e);
    }
  }
}
