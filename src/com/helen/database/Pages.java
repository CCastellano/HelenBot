package com.helen.database;

import com.helen.commands.Command;
import com.helen.commands.CommandData;
import com.helen.database.selectable.*;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.apache.log4j.Logger;
import org.apache.xmlrpc.XmlRpcException;
import org.apache.xmlrpc.client.XmlRpcClient;
import org.apache.xmlrpc.client.XmlRpcClientConfigImpl;
import org.apache.xmlrpc.client.XmlRpcSun15HttpTransportFactory;
import org.jibble.pircbot.Colors;

import java.io.*;
import java.net.URL;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.helen.database.Connector.getConnection;

public class Pages {

	private static final long YEARS = 1000 * 60 * 60 * 24 * 365L;
	private static final long DAYS = 1000 * 60 * 60 * 24L;
	private static final long HOURS = 1000 * 60 * 60L;
	private static final long MINUTES = 1000 * 60L;
	private static final Logger logger = Logger.getLogger(Pages.class);
	private static final CommandLineParser parser = new DefaultParser();
	private static long lastLc = System.currentTimeMillis() - 20000;

	private static final Options searchOpts = new Options() {{
		this.addOption("e", "exclude", true, "exclude page titles");
		this.addOption("t", "tag", true, "limit to a tag");
		this.addOption("a", "author", true, "limit to an author");
		this.addOption("u", "summary", false, "summarize results");
	}};

	private static XmlRpcClient client;
	static {
		XmlRpcClientConfigImpl config = new XmlRpcClientConfigImpl();
		try {
			config.setServerURL(new URL(Configs.getSingleProperty("wikidotServer").getValue()));
			config.setBasicUserName(Configs.getSingleProperty("appName").getValue());
			config.setBasicPassword(Configs.getSingleProperty("wikidotapikey").getValue());
			config.setEnabledForExceptions(true);
			config.setConnectionTimeout(10 * 1000);
			config.setReplyTimeout(30 * 1000);

			client = new XmlRpcClient();
			client.setTransportFactory(new XmlRpcSun15HttpTransportFactory(client));
			client.setTypeFactory(new XmlRpcTypeNil(client));
			client.setConfig(config);
		} catch (Exception e) {
			logger.error("Exception initializing XML-RPC", e);
		}
	}

	private static Object pushToAPI(String method, Object... params) throws XmlRpcException {
		return client.execute(method, params);
	}

	private static String fmtRating(int rating) {
		return (rating > 0 ? "+" : "") + rating;
	}

	public static ArrayList<String> lastCreated() {
		if (System.currentTimeMillis() - lastLc > 15000) {
			ArrayList<String> pagelist = new ArrayList<>();
			try {
				String regex = "<td style=\"vertical-align: top;\"><a href=\"/(.+)\">(.+)</a></td>";
				Pattern r = Pattern.compile(regex);
				String s;
				URL u = new URL("http://www.scp-wiki.net/most-recently-created");
				InputStream is = u.openStream();
				BufferedReader br = new BufferedReader(new InputStreamReader(is));
				int i = 0;
				while ((s = br.readLine()) != null) {
					Matcher m = r.matcher(s);
					if (m.matches()) {
						if (i++ < 3) {
							pagelist.add(m.group(1));
						} else {
							br.close();
							break;
						}
					}
				}
			} catch (Exception e) {
				logger.error("There was an exception attempting to grab last created", e);
			}
			lastLc = System.currentTimeMillis();
			return pagelist;
		}

		return null;
	}

	private static String getTitle(String pagename) {
		String pageName = null;
		try {
			CloseableStatement stmt = Connector.getStatement(Queries.getQuery("getPageByName"), pagename);
			ResultSet rs = stmt.getResultSet();
			if (rs != null && rs.next()) {
				pageName = rs.getString("title");
				if (rs.getBoolean("scppage")) {
					if (!rs.getString("scptitle").equalsIgnoreCase("[ACCESS DENIED]")) {
						pageName = rs.getString("scptitle");
					}
				}
			}
			rs.close();
			stmt.close();
		} catch (Exception e) {
			logger.error("Exception getting title", e);
		}
		return pageName;

	}

	public static String getPageInfo(String pagename, CommandData data) {
		return getPageInfo(pagename, Configs.commandEnabled(data, "lcratings"));
	}

	public static String getPageInfo(String pagename) {
		return getPageInfo(pagename, true);
	}

	public static String getPageInfo(String pagename, boolean ratingEnabled) {
		SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
		String targetName = pagename.toLowerCase();
		Map<String, Object> params = new HashMap<>();
		params.put("site", Configs.getSingleProperty("site").getValue());
		String[] target = new String[]{targetName.toLowerCase()};
		params.put("pages", target);
		/*
		ArrayList<String> keyswewant = new ArrayList<>();
		keyswewant.add("title_shown");
		keyswewant.add("rating");
		keyswewant.add("created_at");
		keyswewant.add("title");
		keyswewant.add("created_by");
		keyswewant.add("tags");
		*/
		try {
			@SuppressWarnings("unchecked")
			HashMap<String, HashMap<String, Object>> result =
					(HashMap<String, HashMap<String, Object>>) pushToAPI("pages.get_meta", params);

			StringBuilder returnString = new StringBuilder(Colors.BOLD);

			String title = getTitle(targetName);
			if (title == null || title.isEmpty() || title.equals("[ACCESS DENIED]")) {
				returnString.append(result.get(targetName).get("title_shown"));
			} else {
				returnString.append(result.get(targetName).get("title_shown"));
				if (!title.equalsIgnoreCase((String) result.get(targetName).get("title_shown"))) {
					returnString.append(": ");
					returnString.append(title);
				}
			}
			returnString.append(Colors.NORMAL);
			returnString.append(" (");
			if (ratingEnabled) {
				returnString.append("Rating: ");
				Integer rating = (Integer) result.get(targetName).get("rating");
				returnString.append(fmtRating(rating));
				returnString.append(". ");
			}
			returnString.append("Written ");
			returnString.append(findTime(df.parse((String) result.get(targetName)
					.get("created_at")).getTime()));
			returnString.append("by ");
			returnString.append(result.get(targetName).get("created_by"));
			returnString.append(")");
			returnString.append(" - ");
			returnString.append("http://scp-wiki.net/");
			returnString.append(targetName);

			return returnString.toString();

		} catch (Exception e) {
			logger.error("There was an exception retreiving metadata", e);
		}

		return Command.NOT_FOUND;
	}

	public static String getAuthorDetail(CommandData data, String user) {
		user = user.toLowerCase();
		try {
			ArrayList<Selectable> authors = new ArrayList<>();
			CloseableStatement stmt =
					Connector.getStatement(Queries.getQuery("findAuthors"), "%" + user + "%");
			ResultSet rs = stmt.getResultSet();
			while (rs != null && rs.next()) {
				authors.add(new Author(rs.getString("created_by")));
			}
			rs.close();
			stmt.close();

			if (authors.isEmpty())
				return "I couldn't find any author by that name.";
			else if (authors.size() > 1)
				return StoredCommands.store(data.getSender(), authors);
			else
				return getAuthorDetailsPages(((Author) authors.get(0)).authorName);
		} catch (Exception e) {
			logger.error("Error constructing author detail", e);
		}

		return Command.ERROR;
	}

	public static String disambiguateWikipedia(CommandData data, List<String> titles) {
		if (titles.isEmpty())
			return "I couldn't find any choices.";
		else {
			ArrayList<Selectable> choices = new ArrayList<>();
			for (String title : titles)
				choices.add(new WikipediaAmbiguous(data, title));
			return StoredCommands.store(data.getSender(), choices);
		}
	}

	private static ArrayList<Page> getTagged(String user, String tag) throws SQLException {
		ArrayList<Page> pages = new ArrayList<>();
		ResultSet rs = Connector
				.getStatement(Queries.getQuery("findTagged"), tag, user, user)
				.getResultSet();
		while (rs != null && rs.next()) {
			pages.add(new Page(
					rs.getString("pagename"),
					rs.getString("title"),
					rs.getInt("rating"),
					rs.getString("created_by"),
					rs.getTimestamp("created_on"),
					rs.getString("scptitle")
			));
		}
		return pages;
	}

	private static boolean count(boolean comma, StringBuilder str, int size, String name) {
		if (size == 0)
			return comma;
		else {
			if (comma)
				str.append(", ");
			str.append(Colors.BOLD);
			str.append(size);
			str.append(Colors.NORMAL);
			str.append(" ");
			str.append(name);
			if (size != 1)
				str.append("s");
			return true;
		}
	}

	public static String getAuthorDetailsPages(String user) {
		String lowerUser = user.toLowerCase();
		Page authorPage = null;
		try {
			CloseableStatement stmt =
					Connector.getStatement(Queries.getQuery("findAuthorPage"), lowerUser);
			ResultSet rs = stmt.getResultSet();
			if (rs != null && rs.next())
				authorPage = new Page(
						rs.getString("pagename"),
						rs.getString("title"),
						rs.getString("scptitle")
				);
			rs.close();
			stmt.close();
			String authorPageName = authorPage == null ? "null" : authorPage.pageLink;

			ArrayList<Page> scps  = getTagged(lowerUser, "scp");
			ArrayList<Page> tales = getTagged(lowerUser, "tale");
			ArrayList<Page> gois  = getTagged(lowerUser, "goi-format");
			ArrayList<Page> hubs  = getTagged(lowerUser, "hub");
			ArrayList<Page> art   = getTagged(lowerUser, "artwork");

			HashSet<Page> all = new HashSet<>(scps);
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

			Page latest = null;
			Timestamp ts = new java.sql.Timestamp(0);
			int rating = 0;

			for (Page pg : all) {
				if (!pg.pageLink.equals(authorPageName)) {
					rating += pg.rating;
					if (pg.createdAt.compareTo(ts) > 0) {
						ts = pg.createdAt;
						latest = pg;
					}
				}
			}

			StringBuilder str = new StringBuilder(Colors.BOLD);
			str.append(user);
			str.append(Colors.NORMAL);

			if (authorPage != null) {
				str.append(" - ");
				str.append("http://www.scp-wiki.net/");
				str.append(authorPage.pageLink);
				str.append(" -");
			}
			str.append(" has ");
			str.append(Colors.BOLD);
			str.append(allSize);
			str.append(Colors.NORMAL);
			str.append(" page");
			if (allSize != 0)
				str.append("s");
			str.append(" (");

			boolean comma = count(false, str, scpsSize, "SCP article");
			comma = count(comma, str, talesSize, "tale");
			comma = count(comma, str, goisSize, "GOI article");
			comma = count(comma, str, hubsSize, "hub");
			count(comma, str, artSize, "artwork page");

			str.append("). They have ");
			str.append(Colors.BOLD);
			str.append(fmtRating(rating));
			str.append(Colors.NORMAL);
			str.append(" net votes with an average of ");
			str.append(Colors.BOLD);
			str.append(fmtRating(Math.round(rating / (float) allSize)));
			str.append(Colors.NORMAL);
			str.append(".  Their latest page is ");
			str.append(Colors.BOLD);

			str.append(latest.title);

			str.append(Colors.NORMAL);
			str.append(" at ");
			str.append(Colors.BOLD);
			str.append(fmtRating(latest.rating));
			str.append(Colors.NORMAL);
			str.append(".");

			return str.toString();
		} catch (Exception e) {
			logger.error("There was an exception retreiving author pages stuff", e);
		}

		return Command.ERROR;
	}

	private static final String[] nothing = new String[0];
	private static String[] safe(String[] x) {
		return x == null ? nothing : x;
	}

	public static String getPotentialTargets(String[] terms, String username) {
		ArrayList<Selectable> potentialPages = new ArrayList<>();
		ArrayList<String> params = new ArrayList<>();
		StringBuilder query =
				new StringBuilder("SELECT * FROM pages WHERE TRUE");
		boolean summarize = false;
		try {
			CommandLine cmd = parser.parse(searchOpts, Arrays.copyOfRange(terms, 1, terms.length));
			summarize = cmd.hasOption('u');
			for (String word : cmd.getArgs()) {
				query.append(" AND (title ILIKE ? OR scptitle ILIKE ?)");
				String escape = "%" + word + "%";
				params.add(escape);
				params.add(escape);
			}
			for (String tag : safe(cmd.getOptionValues('t'))) {
				query.append(" AND EXISTS (");
				query.append("SELECT FROM pagetags JOIN tags ON pagetags.tagid = tags.tagid");
				query.append(" WHERE pages.pageid = pagetags.pageid AND tags.tag = ?)");
				params.add(tag);
			}
			for (String exclude : safe(cmd.getOptionValues('e'))) {
				query.append(" AND title NOT ILIKE ? AND scptitle NOT ILIKE ?");
				String escape = "%" + exclude + "%";
				params.add(escape);
				params.add(escape);
			}
			for (String author : safe(cmd.getOptionValues('a'))) {
				query.append(" AND (created_by = ? OR EXISTS (");
				query.append("SELECT FROM attributions WHERE attributions.pageid = pages.pageid");
				query.append(" AND attributions.created_by = ?))");
				String authorLower = author.toLowerCase();
				params.add(authorLower);
				params.add(authorLower);
			}
			Connection conn = getConnection();
			PreparedStatement stmt = conn.prepareStatement(query.toString());
			for (int i = 0; i < params.size(); i++)
				stmt.setString(i + 1, params.get(i));
			ResultSet rs = stmt.executeQuery();
			while (rs != null && rs.next())
				potentialPages.add(new Page(
						rs.getString("pagename"),
						rs.getString("title"),
						rs.getInt("rating"),
						rs.getString("created_by"),
						rs.getTimestamp("created_on"),
						rs.getString("scptitle")
				));
			stmt.close();
			conn.close();
			if (rs != null)
				rs.close();
		} catch (Exception e) {
			logger.error("There was an issue during search", e);
		}
		if (summarize && !potentialPages.isEmpty()) {
			int rating = 0;
			HashSet<String> authors = new HashSet<>();
			Timestamp oldest = null;
			Timestamp newest = null;
			Page highest = null;

			for (Selectable x : potentialPages) {
				Page page = (Page) x;
				rating += page.rating;
				authors.add(page.createdBy);
				if (oldest == null || page.createdAt.before(oldest))
					oldest = page.createdAt;
				if (newest == null || page.createdAt.after(newest))
					newest = page.createdAt;
				if (highest == null || highest.rating < page.rating)
					highest = page;
			}

			return "Found " +
					Colors.BOLD + potentialPages.size() + Colors.NORMAL +
					" pages by " +
					Colors.BOLD + authors.size() + Colors.NORMAL +
					" authors. They have a total rating of " +
					Colors.BOLD + fmtRating(rating) + Colors.NORMAL +
					", with an average of " +
					Colors.BOLD + fmtRating(rating / potentialPages.size()) + Colors.NORMAL +
					". The pages were created between " +
					findTime(oldest.getTime()) + "and " + findTime(newest.getTime()) +
					". The highest rated page is " +
					Colors.BOLD + highest.title + Colors.NORMAL +
					" at " +
					Colors.BOLD + fmtRating(highest.rating) + Colors.NORMAL +
					".";
		} else if (potentialPages.size() > 1)
			return StoredCommands.store(username, potentialPages);
		else if (potentialPages.size() == 1)
			return getPageInfo(((Page) potentialPages.get(0)).pageLink);
		else
			return Command.NOT_FOUND;
	}

	public static String getStoredInfo(String index, String username) {
		return StoredCommands.run(index, username);
	}

	private static String measureTime(long amount, String label) {
		return amount + " " + label + (amount == 1 ? " " : "s ") + "ago ";
	}

	public static String findTime(long time) {
		//compensate for EST (helen runs in EST)
		time = (System.currentTimeMillis() + HOURS * 4) - time;
		if (time >= YEARS)
			return measureTime(time / YEARS, "year");
		else if (time >= DAYS)
			return measureTime(time / DAYS, "day");
		else if (time >= HOURS)
			return measureTime(time / HOURS, "hour");
		else if (time >= MINUTES)
			return measureTime(time / MINUTES, "minute");
		else
			return "a few seconds ago";
	}
}
