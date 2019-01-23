package com.helen.database;

import com.helen.commands.Command;
import com.helen.commands.CommandData;
import com.helen.search.WikipediaSearch;
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

public class Pages {

	private static final long YEARS = 1000 * 60 * 60 * 24 * 365L;
	private static final long DAYS = 1000 * 60 * 60 * 24L;
	private static final long HOURS = 1000 * 60 * 60L;
	private static final long MINUTES = 1000 * 60L;
	private static final Logger logger = Logger.getLogger(Pages.class);
	private static XmlRpcClient client;
	private static long lastLc = System.currentTimeMillis() - 20000;
	private static HashMap<String, ArrayList<Selectable>> storedEvents = new HashMap<>();

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
			logger.error("There was an exception", e);
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

			StringBuilder returnString = new StringBuilder();
			returnString.append(Colors.BOLD);

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
			returnString.append("by: ");
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

			if (authors.isEmpty()) {
				return "I couldn't find any author by that name.";
			}

			if (authors.size() > 1) {
				storedEvents.put(data.getSender(), authors);
				StringBuilder str = new StringBuilder();
				str.append("Did you mean: ");
				String prepend = "";
				for (Selectable author : authors) {
					str.append(prepend);
					prepend = ",";
					str.append(Colors.BOLD);
					str.append(((Author) author).getAuthor());
					str.append(Colors.NORMAL);
				}
				str.append("?");
				return str.toString();
			} else {
				return getAuthorDetailsPages(((Author) authors.get(0)).getAuthor());
			}

		} catch (Exception e) {
			logger.error("Error constructing author detail", e);
		}

		return "I apologize, there's been an error.  Please inform DrMagnus there's an error with author details.";
	}

	public static String disambiguateWikipedia(CommandData data, List<String> titles) {
		if (titles.isEmpty()) {
			return "I couldn't find any choices.";
		}
		try {
			ArrayList<Selectable> choices = new ArrayList<>();
			for (String title : titles) {
				choices.add(new WikipediaAmbiguous(data, title));
			}
			storedEvents.put(data.getSender(), choices);
			StringBuilder str = new StringBuilder();
			str.append("Did you mean: ");
			String prepend = "";
			for (Selectable choice : choices) {
				str.append(prepend);
				prepend = ",";
				str.append(Colors.BOLD);
				str.append(((WikipediaAmbiguous) choice).getTitle());
				str.append(Colors.NORMAL);
			}
			str.append("?");
			String result = str.toString();
			result = result.substring(0, Math.min(400, result.length()));
			int lastComma = result.lastIndexOf(',');
			if (lastComma != -1) {
				result = result.substring(0, lastComma);
			}
			return result;
		} catch (Exception e) {
			logger.error("Error constructing choice", e);
		}

		return Command.ERROR;
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
					rs.getBoolean("scppage"),
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
						rs.getBoolean("scppage"),
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

			StringBuilder str = new StringBuilder();
			str.append(Colors.BOLD);
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
			if (latest.scpPage) {
				str.append(": ");
				str.append(latest.scpTitle);
			}

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

	public static String getPotentialTargets(String[] terms, String username) {
		boolean exact = terms[1].equalsIgnoreCase("-e");
		int indexOffset = exact ? 2 : 1;
		ArrayList<Selectable> potentialPages = new ArrayList<>();
		String[] lowerterms = new String[terms.length - indexOffset];
		for (int i = indexOffset; i < terms.length; i++) {
			lowerterms[i - indexOffset] = terms[i].toLowerCase();
			logger.info(lowerterms[i - indexOffset]);
		}
		try {
			CloseableStatement stmt = null;
			Connection conn = null;
			ResultSet rs;
			PreparedStatement state;
			if (exact) {
				stmt = Connector.getArrayStatement(Queries.getQuery("findskips"), lowerterms);
				logger.info(stmt.toString());
				rs = stmt.getResultSet();
			} else {
				StringBuilder query =
						new StringBuilder("select pagename,title,scptitle,scppage from pages where");
				for (int j = indexOffset; j < terms.length; j++) {
					if (j != indexOffset) {
						query.append(" and");
					}
					query.append(" lower(coalesce(scptitle, title)) like ?");
				}
				conn = Connector.getConnection();
				state = conn.prepareStatement(query.toString());
				for (int j = indexOffset; j < terms.length; j++) {
					state.setString(j - (indexOffset - 1), "%" + terms[j].toLowerCase() + "%");
				}
				logger.info(state.toString());
				rs = state.executeQuery();
			}
			while (rs != null && rs.next()) {
				potentialPages.add(new Page(
						rs.getString("pagename"),
						rs.getString("title"),
						rs.getBoolean("scppage"),
						rs.getString("scptitle")
				));
			}
			if (stmt != null) stmt.close();
			if (conn != null) conn.close();
			if (rs != null)   rs.close();
		} catch (SQLException e) {
			logger.error("There was an issue grabbing potential SCP pages", e);
		}

		if (potentialPages.size() > 1) {
			storedEvents.put(username, potentialPages);
			StringBuilder str = new StringBuilder();
			str.append("Did you mean: ");

			for (Selectable p : potentialPages) {
				Page page = (Page) p;
				str.append(Colors.BOLD);
				str.append(page.scpPage ? page.title + ": " + page.scpTitle : page.title);
				str.append(Colors.NORMAL);
				str.append(", ");
			}
			str.append("?");
			return str.toString();
		} else {
			if (potentialPages.size() == 1) {
				return getPageInfo(((Page) potentialPages.get(0)).pageLink);
			} else {
				return Command.NOT_FOUND;
			}
		}
	}

	public static String getStoredInfo(String index, String username) {
		try {
			Selectable s = storedEvents.get(username).get(Integer.parseInt(index) - 1);
			if (s instanceof Page) {
				return getPageInfo((String) s.selectResource());
			} else if (s instanceof Author) {
				return getAuthorDetailsPages((String) s.selectResource());
			} else if (s instanceof WikipediaAmbiguous) {
				WikipediaAmbiguous choice = (WikipediaAmbiguous) s;
				CommandData data = choice.getData();
				return WikipediaSearch.search(data, choice.getTitle());
			}

		} catch (Exception e) {
			logger.error("There was an exception getting stored info", e);
		}

		return "Either the command was malformed, or I have nothing for you to get.";
	}

	private static String measureTime(long amount, String label) {
		StringBuilder str = new StringBuilder(Long.toString(amount));
		str.append(" ");
		str.append(label);
		if (amount != 1)
			str.append("s");
		str.append(" ago");
		return str.toString();
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