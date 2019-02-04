package com.helen.background;

import com.helen.*;
import com.helen.database.*;
import org.apache.log4j.Logger;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import javax.annotation.Nullable;
import java.io.IOException;
import java.net.URL;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.Collection;
import java.util.stream.Collectors;

public class StaffFetcher {
  private static final Logger logger = Logger.getLogger(StaffFetcher.class);

  private static final String PAGE    = "http://05command.wikidot.com/staff-list";
  private static final int TIMEOUT    = 5_000; // Five seconds in milliseconds.

  @Nullable
  private static String fromTable(Elements tr, int i) {
    String text = tr.get(i).text();
    return notBlank(text) ? text : null;
  }

  private static boolean notBlank(String str) {
    switch (str) {
      case "":
      case "-":
      case "No":
      case "no":
      case "None":
      case "none":
        return false;
      default:
        return true;
    }
  }

  // Parses the metadata page and batch-inserts attributions into the database.
  public static void update() {
    Timestamp now = new Timestamp(System.currentTimeMillis());
    try (
        Connection conn = Connector.getConnection();
        PreparedStatement clearCaptains = Connector.prepare(conn, "clear_captains",
            "UPDATE teams SET captain_id = null"
        );
        PreparedStatement clearMembers = Connector.prepare(conn, "clear_members",
            "DELETE FROM team_members"
        );
        PreparedStatement insertTeam = Connector.prepare(conn, "make_new_team",
            "INSERT INTO teams (team_name) VALUES (?) ON CONFLICT DO NOTHING"
        );
        PreparedStatement insertStaff = Connector.prepare(conn, "insert_staff",
            "INSERT INTO staff " +
            "(staff_id, username, timezone, contact_methods, activity_level, level, displayname, " +
            "updatetime) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?)" +
            "ON CONFLICT (staff_id) DO UPDATE SET " +
            "username = excluded.username, timezone = excluded.timezone, " +
            "contact_methods = excluded.contact_methods, " +
            "activity_level = excluded.activity_level, level = excluded.level, " +
            "displayname = excluded.displayname, updatetime = excluded.updatetime"
        );
        PreparedStatement insertMember = Connector.prepare(conn, "add_to_team",
            "INSERT INTO team_members (team_id, staff_id) " +
            "SELECT team_id, ? FROM teams WHERE team_name = ?"
        );
        PreparedStatement insertCaptain = Connector.prepare(conn, "insert_captain",
            "UPDATE teams SET captain_id = ? WHERE team_name = ?"
        );
        PreparedStatement deleteOldStaff = Connector.prepare(conn, "deleteOldStaff",
            "DELETE FROM staff WHERE updatetime < ?",
            now
        )
    ) {
      Document doc = Jsoup.parse(new URL(PAGE), TIMEOUT);
      Collection<String> teams = doc
          .select(".wiki-content-table tr")
          .stream()
          .map(x -> x.getElementsByTag("td"))
          .filter(x -> x.size() == 6)
          .flatMap(x -> Arrays.stream(Utils.split(',', x.get(1).text())))
          .filter(StaffFetcher::notBlank)
          .collect(Collectors.toSet());

      for (String team : teams) {
        insertTeam.setString(1, team);
        insertTeam.addBatch();
      }
      insertTeam.executeBatch();

      for (Element table : doc.getElementsByClass("wiki-content-table")) {
        String level = table.previousElementSibling().text();
        for (Element tr : table.getElementsByTag("tr")) {
          Elements tds = tr.getElementsByTag("td");
          if (tds.size() == 6) {
            String username = null;
            int wikidotid = -1;
            for (Element a : tds.get(0).getElementsByTag("a")) {
              String href = a.attr("href");
              int slash = href.lastIndexOf('/');
              if (slash != -1) {
                username = href.substring(slash + 1);
              }
              String onclick = a.attr("onclick");
              int openParen = onclick.indexOf('(');
              int closeParen = onclick.lastIndexOf(')');
              if (openParen != -1 && closeParen != -1) {
                try {
                  wikidotid = Integer.parseInt(onclick.substring(openParen + 1, closeParen));
                } catch (NumberFormatException e) {
                  logger.warn("Unable to parse wikidot id", e);
                }
              }
            }
            if (wikidotid == -1 || username == null) {
              logger.warn("Unable to parse staff info for " + tds.get(0).text());
              continue;
            }
            String displayname = tds.get(0).text();
            String timezone = fromTable(tds, 2);
            String activity_level = tds.get(3).text();
            String contact_methods = fromTable(tds, 4);

            insertStaff.setInt(1, wikidotid);
            insertStaff.setString(2, username);
            insertStaff.setString(3, timezone);
            insertStaff.setString(4, contact_methods);
            insertStaff.setString(5, activity_level);
            insertStaff.setString(6, level);
            insertStaff.setString(7, displayname);
            insertStaff.setTimestamp(8, now);

            insertStaff.addBatch();

            for (String team : Utils.split(',', tds.get(1).text())) {
              if (notBlank(team)) {
                insertMember.setInt(1, wikidotid);
                insertMember.setString(2, team);
                insertMember.addBatch();
              }
            }
            for (String team : Utils.split(',', tds.get(5).text())) {
              if (notBlank(team)) {
                insertCaptain.setInt(1, wikidotid);
                insertCaptain.setString(2, team);
                insertCaptain.addBatch();
              }
            }
          }
        }
      }
      clearCaptains.executeUpdate();
      clearMembers.executeUpdate();
      insertStaff.executeBatch();
      insertMember.executeBatch();
      deleteOldStaff.executeUpdate();
      insertCaptain.executeBatch();
    } catch (IOException | SQLException e) {
      logger.error("Staff fetcher error", e);
    }
  }
}
