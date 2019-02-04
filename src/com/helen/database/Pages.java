package com.helen.database;

import com.helen.*;
import com.helen.commands.*;
import com.helen.database.selectable.*;
import com.helen.error.*;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.log4j.Logger;
import org.apache.xmlrpc.XmlRpcException;
import org.apache.xmlrpc.client.XmlRpcClient;
import org.apache.xmlrpc.client.XmlRpcClientConfigImpl;
import org.apache.xmlrpc.client.XmlRpcSun15HttpTransportFactory;
import org.jibble.pircbot.Colors;

import javax.annotation.Nullable;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Pages {
  private static final Logger logger = Logger.getLogger(Pages.class);

  private static final CommandLineParser CLI = new DefaultParser();
  private static final String[] STRING_0     = new String[0];
  private static final long LC_COOLDOWN      = 15_000;
  private static final String LC_PAGE        = "http://www.scp-wiki.net/most-recently-created";
  private static final int NUM_LC_RESULTS    = 3;
  private static final String URL            = "https://www.wikidot.com/xml-rpc-api.php";
  private static final Pattern TD_PATTERN    =
      Pattern.compile("<td style=\"vertical-align: top;\"><a href=\"/(.+)\">(.+)</a></td>");

  private static long lastLc = System.currentTimeMillis() - 20_000;

  private static final Options searchOpts = new Options() {{
    this.addOption("e", "exclude", true, "exclude page titles");
    this.addOption("t", "tag", true, "limit to a tag");
    this.addOption("a", "author", true, "limit to an author");
    this.addOption("u", "summary", false, "summarize results");
  }};

  private static final XmlRpcClient client = new XmlRpcClient() {{
    try {
      XmlRpcClientConfigImpl config = new XmlRpcClientConfigImpl();
      config.setServerURL(new URL(URL));
      config.setBasicUserName(Configs.getSingleProperty("appName").value);
      config.setBasicPassword(Configs.getSingleProperty("wikidotapikey").value);
      config.setEnabledForExceptions(true);
      config.setConnectionTimeout(10_000);
      config.setReplyTimeout(30_000);

      this.setTransportFactory(new XmlRpcSun15HttpTransportFactory(this));
      this.setTypeFactory(new XmlRpcTypeNil(this));
      this.setConfig(config);
    } catch (MalformedURLException e) {
      logger.error("Exception initializing XML-RPC", e);
      throw new InvalidConfigurationException("Invalid XML-RPC settings", e);
    }
  }};

  @SuppressWarnings("unchecked")
  public static <T> T pushToAPI(String method, Map<String, Object> params) throws XmlRpcException {
    params.put("site", Configs.getSingleProperty("site").value);
    Object o = client.execute(method, new Object[]{params});
    try {
      return (T) o;
    } catch (ClassCastException e) {
      throw new XmlRpcException("Invalid response", e);
    }
  }

  public static String[] listPages() throws XmlRpcException {
    Map<String, Object> params = new HashMap<>();
    params.put("order", "created_at desc");
    Object[] pages = pushToAPI("pages.select", params);
    return Arrays
        .stream(pages)
        .map(Object::toString)
        .filter(x -> !x.contains(":"))
        .toArray(String[]::new);
  }

  public static void walk(String[] titles, BiConsumer<Page, String[]> f)
      throws XmlRpcException {
    for (String[] chunk : Utils.chunk(10, titles)) {
      Map<String, Object> params = new HashMap<>();
      params.put("pages", chunk);
      Map<String, Map<String, Object>> result = pushToAPI("pages.get_meta", params);
      for (Map<String, Object> obj : result.values()) {
        Object[] tags = (Object[]) obj.get("tags");
        f.accept(new Page(obj), Arrays.copyOf(tags, tags.length, String[].class));
      }
    }
  }

  public static String[] lastCreated(CommandData data)
      throws IncorrectUsageException, IOException, XmlRpcException, SQLException {
    if (System.currentTimeMillis() - lastLc <= LC_COOLDOWN) {
      throw new IncorrectUsageException("I can't do that yet.");
    }
    lastLc = System.currentTimeMillis();
    URL u = new URL(LC_PAGE);
    try (
        InputStream is = u.openStream();
        BufferedReader br = new BufferedReader(new InputStreamReader(is))
    ) {
      String[] lc = new String[NUM_LC_RESULTS];
      int i = 0;
      String s;
      while ((s = br.readLine()) != null) {
        Matcher m = TD_PATTERN.matcher(s);
        if (m.matches()) {
          if (i < lc.length) {
            lc[i++] = m.group(1);
          } else {
            break;
          }
        }
      }
      return getPageInfo(lc, Configs.commandEnabled(data, "lcratings"));
    }
  }

  @Nullable
  private static String getTitle(String pagename) throws SQLException {
    try (
        Connection conn = Connector.getConnection();
        PreparedStatement stmt = Connector.prepare(conn, "getPageByName",
            "SELECT * FROM pages WHERE pagename = ?",
            pagename
        );
        ResultSet rs = stmt.executeQuery()
    ) {
      if (rs.next()) {
        String scptitle = rs.getString("scptitle");
        if (!"[ACCESS DENIED]".equalsIgnoreCase(scptitle)) {
          return scptitle;
        }
      }
      return null;
    }
  }

  @Nullable
  public static String getPageInfo(String pagename) throws XmlRpcException, SQLException {
    return getPageInfo(pagename, true);
  }

  private static String parsePageInfo(Map<String, Object> obj, boolean ratingEnabled)
      throws SQLException {
    StringBuilder str = new StringBuilder(Colors.BOLD);

    String title = getTitle((String) obj.get("fullname"));
    str.append(obj.get("title_shown"));
    if (title != null && !title.isEmpty()) {
      str .append(": ")
          .append(title);
    }
    str .append(Colors.NORMAL)
        .append(" (");
    if (ratingEnabled) {
      str .append("Rating: ")
          .append(Utils.fmtRating((int) obj.get("rating")))
          .append("; ");
    }
    System.out.println(obj.get("created_at"));
    return str
        .append("written ")
        .append(Utils.findTime(Utils.parseZonedTime((String) obj.get("created_at"))))
        .append(" by ")
        .append(obj.get("created_by"))
        .append(") - http://scp-wiki.net/")
        .append(obj.get("fullname"))
        .toString();
  }

  public static String[] getPageInfo(String[] pagenames, boolean ratingEnabled)
      throws XmlRpcException, SQLException {
    for (int i = 0; i < pagenames.length; i++) {
      pagenames[i] = pagenames[i].toLowerCase();
    }

    Map<String, Object> params = new HashMap<>();
    params.put("pages", pagenames);
    try {
      Map<String, Map<String, Object>> result = pushToAPI("pages.get_meta", params);

      String[] results = new String[pagenames.length];
      for (int i = 0; i < results.length; i++) {
        results[i] = parsePageInfo(result.get(pagenames[i]), ratingEnabled);
      }
      return results;
    } catch (ClassCastException ignored) {
      return STRING_0;
    }
  }

  @Nullable
  public static String getPageInfo(String pagename, boolean ratingEnabled)
      throws XmlRpcException, SQLException {
    String[] results = getPageInfo(new String[]{pagename}, ratingEnabled);
    return results.length == 0 ? null : results[0];
  }

  public static String getAuthorDetail(CommandData data, String user) throws SQLException {
    user = user.toLowerCase();
    try (
        Connection conn = Connector.getConnection();
        PreparedStatement stmt = Connector.prepare(conn, "findAuthors",
            "SELECT (created_by) FROM pages WHERE created_by ILIKE ? " +
            "UNION SELECT (created_by) FROM attributions WHERE created_by ILIKE ?",
            '%' + user + '%', '%' + user + '%'
        );
        ResultSet rs = stmt.executeQuery()
    ) {
      List<Selectable> authors = new ArrayList<>();
      while (rs.next()) {
        authors.add(new Author(rs));
      }

      if (authors.isEmpty()) {
        return "I couldn't find any author by that name.";
      } else if (authors.size() > 1) {
        return StoredCommands.store(data.sender, authors);
      } else {
        return getAuthorDetailsPages(((Author) authors.get(0)).authorName);
      }
    }
  }

  private static List<Page> getTagged(String user, String tag) throws SQLException {
    List<Page> pages = new ArrayList<>();
    try (
        Connection conn = Connector.getConnection();
        PreparedStatement stmt = Connector.prepare(conn, "findTagged",
            "SELECT * FROM pages " +
            "JOIN pagetags ON pages.pageid = pagetags.pageid " +
            "JOIN tags ON pagetags.tagid = tags.tagid " +
            "WHERE tags.tag = ? AND (created_by = ? OR pages.pageid IN " +
            "(SELECT pageid FROM attributions WHERE attributions.created_by = ?))",
            tag, user, user
        );
        ResultSet rs = stmt.executeQuery()
    ) {
      while (rs.next()) {
        pages.add(new Page(rs));
      }
    }
    return pages;
  }

  public static String getAuthorDetailsPages(String user) throws SQLException {
    String lowerUser = user.toLowerCase();
    try (
        Connection conn = Connector.getConnection();
        PreparedStatement findAuthorPage = Connector.prepare(conn, "findAuthorPage",
            "SELECT * FROM pages WHERE pagename = ?",
            lowerUser
        );
        PreparedStatement findLatest = Connector.prepare(conn, "findLatest",
            "SELECT * FROM PAGES WHERE created_by = ? OR pages.pageid IN " +
            "(SELECT pageid FROM attributions WHERE attributions.created_by = ?) " +
            "ORDER BY created_on DESC LIMIT 1",
            lowerUser, lowerUser
        );
        ResultSet rsAuthorPage = findAuthorPage.executeQuery();
        ResultSet rsLatest = findLatest.executeQuery()
    ) {
      Page authorPage = rsAuthorPage.next() ? new Page(rsAuthorPage) : null;
      Page latest = rsLatest.next() ? new Page(rsLatest) : null;

      String authorPageName = authorPage == null ? null : authorPage.pageName;

      Collection<Page> scps  = getTagged(lowerUser, "scp");
      Collection<Page> tales = getTagged(lowerUser, "tale");
      Collection<Page> gois  = getTagged(lowerUser, "goi-format");
      Collection<Page> hubs  = getTagged(lowerUser, "hub");
      Collection<Page> art   = getTagged(lowerUser, "artwork");

      Collection<Page> all = new HashSet<>(scps);
      all.addAll(tales);
      all.addAll(gois);
      all.addAll(hubs);
      all.addAll(art);

      int scpsSize  = scps.size();
      int talesSize = tales.size();
      int goisSize  = gois.size();
      int hubsSize  = hubs.size();
      int artSize   = art.size();
      int allSize   = all.size();

      int rating = all
          .stream()
          .filter(x -> !x.pageName.equals(authorPageName))
          .mapToInt(x -> x.rating)
          .sum();

      StringBuilder str = new StringBuilder(Colors.BOLD)
          .append(user)
          .append(Colors.NORMAL);

      if (authorPage != null) {
        str .append(" - ")
            .append("http://www.scp-wiki.net/")
            .append(authorPage.pageName)
            .append(" -");
      }
      str .append(" has ")
          .append(Colors.BOLD)
          .append(allSize)
          .append(Colors.NORMAL)
          .append(" page");
      if (allSize > 0) {
        str.append('s');
      }
      str.append(" (");

      boolean comma = Utils.count(false, str, scpsSize, "SCP article");
      comma = Utils.count(comma, str, talesSize, "tale");
      comma = Utils.count(comma, str, goisSize, "GOI article");
      comma = Utils.count(comma, str, hubsSize, "hub");
      Utils.count(comma, str, artSize, "artwork page");

      str .append("). They have ")
          .append(Colors.BOLD)
          .append(Utils.fmtRating(rating))
          .append(Colors.NORMAL)
          .append(" net votes with an average of ")
          .append(Colors.BOLD)
          .append(Utils.fmtRating(StrictMath.round(rating / (float) allSize)))
          .append(Colors.NORMAL)
          .append('.');
      if (latest != null) {
        str .append("  Their latest page is ")
            .append(Colors.BOLD)
            .append(latest.title)
            .append(Colors.NORMAL)
            .append(" at ")
            .append(Colors.BOLD)
            .append(Utils.fmtRating(latest.rating))
            .append(Colors.NORMAL)
            .append('.');
      }
      return str.toString();
    }
  }

  private static String[] safe(@Nullable String[] x) {
    return x == null ? STRING_0 : x;
  }

  @Nullable
  public static String getPotentialTargets(String[] terms, String username)
      throws XmlRpcException, SQLException, ParseException {
    StringBuilder str = new StringBuilder("SELECT * FROM pages WHERE TRUE" );
    CommandLine cmd = CLI.parse(searchOpts, Arrays.copyOfRange(terms, 1, terms.length));
    boolean summarize = cmd.hasOption('u');
    Collection<String> params = new ArrayList<>();
    for (String word : cmd.getArgs()) {
      str.append(" AND (title ILIKE ? OR scptitle ILIKE ?)" );
      String escape = '%' + word + '%';
      params.add(escape);
      params.add(escape);
    }
    for (String tag : safe(cmd.getOptionValues('t'))) {
      str.append(" AND pageid IN (" )
         .append("SELECT pageid FROM pagetags JOIN tags ON pagetags.tagid = tags.tagid " )
         .append("WHERE tags.tag = ?)" );
      params.add(tag);
    }
    for (String exclude : safe(cmd.getOptionValues('e'))) {
      str.append(" AND title NOT ILIKE ? AND scptitle NOT ILIKE ?" );
      String escape = '%' + exclude + '%';
      params.add(escape);
      params.add(escape);
    }
    for (String author : safe(cmd.getOptionValues('a'))) {
      str.append(" AND (created_by = ? OR pageid IN " )
         .append("(SELECT pageid FROM attributions WHERE attributions.created_by = ?))" );
      String authorLower = author.toLowerCase();
      params.add(authorLower);
      params.add(authorLower);
    }
    List<Selectable> potentialPages = new ArrayList<>();
    try (
        Connection conn = Connector.getConnection();
        PreparedStatement stmt = Connector.prepare(conn, null, str.toString(), params.toArray());
        ResultSet rs = stmt.executeQuery()
    ) {
      while (rs.next()) {
        potentialPages.add(new Page(rs));
      }
    }
    if (summarize && !potentialPages.isEmpty()) {
      int rating = 0;
      Collection<String> authors = new HashSet<>();
      Timestamp oldest = null;
      Timestamp newest = null;
      Page highest = null;

      for (Selectable x : potentialPages) {
        Page page = (Page) x;
        rating += page.rating;
        if (page.createdBy != null) {
          authors.add(page.createdBy);
        }
        if (oldest == null || page.createdOn.before(oldest)) {
          oldest = page.createdOn;
        }
        if (newest == null || page.createdOn.after(newest)) {
          newest = page.createdOn;
        }
        if (highest == null || highest.rating < page.rating) {
          highest = page;
        }
      }

      return "Found " +
             Colors.BOLD + potentialPages.size() + Colors.NORMAL +
             " pages by " +
             Colors.BOLD + authors.size() + Colors.NORMAL +
             " authors. They have a total rating of " +
             Colors.BOLD + Utils.fmtRating(rating) + Colors.NORMAL +
             ", with an average of " +
             Colors.BOLD + Utils.fmtRating(rating / potentialPages.size()) + Colors.NORMAL +
             ". The pages were created between " +
             Utils.findTime(oldest.getTime()) + " and " + Utils.findTime(newest.getTime()) +
             ". The highest rated page is " +
             Colors.BOLD + highest.title + Colors.NORMAL +
             " at " +
             Colors.BOLD + Utils.fmtRating(highest.rating) + Colors.NORMAL +
             '.';
    } else if (potentialPages.size() > 1) {
      return StoredCommands.store(username, potentialPages);
    } else if (potentialPages.size() == 1) {
      return getPageInfo(((Page) potentialPages.get(0)).pageName);
    } else {
      return null;
    }
  }
}
