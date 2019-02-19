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

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.InputStream;
import java.net.URL;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class Pages {

	
	private static final Long YEARS = 1000 * 60 * 60 * 24 * 365l;
	private static final Long DAYS = 1000 * 60 * 60 * 24l;
	private static final Long HOURS = 1000 * 60l * 60;
	private static final Long MINUTES = 1000 * 60l;
	private static final Logger logger = Logger.getLogger(Pages.class);
	private static XmlRpcClientConfigImpl config;
	private static XmlRpcClient client;
	private static Long lastLc = System.currentTimeMillis() - 20000;
	private static HashMap<String, ArrayList<Selectable>> storedEvents = new HashMap<String, ArrayList<Selectable>>();

	static {
		config = new XmlRpcClientConfigImpl();
		try {
			config.setServerURL(new URL(Configs.getSingleProperty(
					"wikidotServer").getValue()));
			config.setBasicUserName(Configs.getSingleProperty("appName")
					.getValue());
			config.setBasicPassword(Configs.getSingleProperty("wikidotapikey")
					.getValue());
			config.setEnabledForExceptions(true);
			config.setConnectionTimeout(10 * 1000);
			config.setReplyTimeout(30 * 1000);

			client = new XmlRpcClient();
			client.setTransportFactory(new XmlRpcSun15HttpTransportFactory(
					client));
			client.setTypeFactory(new XmlRpcTypeNil(client));
			client.setConfig(config);

		} catch (Exception e) {
			logger.error("There was an exception", e);
		}

	}

	private static Object pushToAPI(String method, Object... params)
			throws XmlRpcException {
		return (Object) client.execute(method, params);
	}

	public static ArrayList<String> lastCreated() {
		if (System.currentTimeMillis() - lastLc > 15000) {
			ArrayList<String> pagelist = new ArrayList<String>();
			try {
				String regex = "<td style=\"vertical-align: top;\"><a href=\"\\/(.+)\">(.+)<\\/a><\\/td>";
				Pattern r = Pattern.compile(regex);
				String s;
				URL u = new URL("http://www.scp-wiki.net/most-recently-created");
				InputStream is = u.openStream();
				DataInputStream dis = new DataInputStream(new BufferedInputStream(is));
				int i = 0;
				while ((s = dis.readLine()) != null) {
					Matcher m = r.matcher(s);
					if (m.matches()) {
						if (i++ < 3) {
							pagelist.add(m.group(1));
						} else {
							dis.close();
							break;
						}
					}
				}
			} catch (Exception e) {
				logger.error(
						"There was an exception attempting to grab last created",
						e);
			}
			lastLc = System.currentTimeMillis();
			return pagelist;
		}

		return null;

	}

	private static String getTitle(String pagename) {
		String pageName = null;
		try {
			CloseableStatement stmt = Connector.getStatement(
					Queries.getQuery("getPageByName"), pagename);
			ResultSet rs = stmt.getResultSet();
			if (rs != null && rs.next()) {
				pageName = rs.getString("title");
				if(rs.getBoolean("scppage")){
					if(!rs.getString("scptitle").equalsIgnoreCase("[ACCESS DENIED]")){
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
	
	public static String getPageInfo(String pagename,CommandData data){
		return getPageInfo(pagename, Configs.commandEnabled(data, "lcratings"));
	}
	
	public static String getPageInfo(String pagename){
		return getPageInfo(pagename, true);
	}

	public static String getPageInfo(String pagename, boolean ratingEnabled) {
		SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
		String targetName = pagename.toLowerCase();
		Map<String, Object> params = new HashMap<String, Object>();
		params.put("site", Configs.getSingleProperty("site").getValue());
		String[] target = new String[] { targetName.toLowerCase() };
		params.put("pages", target);
		ArrayList<String> keyswewant = new ArrayList<String>();
		keyswewant.add("title_shown");
		keyswewant.add("rating");
		keyswewant.add("created_at");
		keyswewant.add("title");
		keyswewant.add("created_by");
		keyswewant.add("tags");
		try {
			@SuppressWarnings("unchecked")
			HashMap<String, HashMap<String, Object>> result = (HashMap<String, HashMap<String, Object>>) pushToAPI(
					"pages.get_meta", params);

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
			if(ratingEnabled){
				returnString.append("Rating: ");
				Integer rating = (Integer) result.get(targetName).get("rating");
				if (rating > 0) {
					returnString.append("+");
				}
				returnString.append(rating);
				returnString.append(". ");
			}
			returnString.append("Written ");
			returnString.append(findTime(df.parse((String) result.get(targetName)
					.get("created_at")).getTime()));

			CloseableStatement stmt = Connector.getStatement(Queries.getQuery("findRewrite"),targetName);
			ResultSet rs = stmt.getResultSet();
			LocalDate date = null;
			Metadata meta = null;
			List<Metadata> finalMetas = new LinkedList<>();
			while(rs != null && rs.next()){
				Metadata m = new Metadata(rs.getString("pagename"),
						rs.getString("username"),
						rs.getString("metadata_type"),
						rs.getString("authorage_date"));

				LocalDate newDate = LocalDate.parse(m.getDate());
				if(meta == null || LocalDate.parse(meta.getDate()).compareTo(newDate) < 0){
					meta = m;
				}else if(LocalDate.parse(meta.getDate()).compareTo(newDate) == 0){
					finalMetas.clear();
					finalMetas.add(meta);
					finalMetas.add(m);
				}
			}

			if(finalMetas.isEmpty() && meta != null){
				finalMetas.add(meta);
			}

            stmt = Connector.getStatement(Queries.getQuery("findAuthors"),targetName);
            rs = stmt.getResultSet();
            Metadata metaAuthor = null;
            List<Metadata> authorFinalMetas = new LinkedList<>();
            while(rs != null && rs.next()){
                Metadata m = new Metadata(rs.getString("pagename"),
                        rs.getString("username"),
                        rs.getString("metadata_type"),
                        rs.getString("authorage_date"));

                authorFinalMetas.add(m);
            }

			returnString.append("By: ");
            if(!authorFinalMetas.isEmpty()){

                if(authorFinalMetas.size() == 1) {
                    returnString.append(authorFinalMetas.get(0).getUsername());
                }else if(authorFinalMetas.size() == 2){
                    returnString.append(authorFinalMetas.get(0).getUsername() + " and " + authorFinalMetas.get(1).getUsername());
                }else{
                    returnString.append(authorFinalMetas.stream().map(metadata -> metadata.getUsername()).collect(Collectors.joining(", ")));
                }
            }else {

                returnString.append(result.get(targetName).get("created_by"));
            }
			if(meta != null){

				returnString.append(" rewritten on: ");
				returnString.append(meta.getDate());
				returnString.append( " by ");
				if(finalMetas.size() == 1) {
					returnString.append(finalMetas.get(0).getUsername());
				}else if(finalMetas.size() == 2){
					returnString.append(finalMetas.get(0).getUsername() + " and " + finalMetas.get(1).getUsername());
				}else{
					returnString.append(finalMetas.stream().map(metadata -> metadata.getUsername()).collect(Collectors.joining(", ")));
				}
			}
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
	
	public static String getAuthorDetail(CommandData data, String user){
		user = user.toLowerCase();
		user = user.trim();
		try{
			
			ArrayList<Selectable> authors = new ArrayList<Selectable>();
			CloseableStatement stmt = Connector.getStatement(Queries.getQuery("findAuthors"), "%" + user + "%");
			ResultSet rs = stmt.getResultSet();
			while(rs != null && rs.next()){
				authors.add(new Author(rs.getString("created_by")));
			}
			rs.close();
			stmt.close();

			if(authors.isEmpty()){
				return "I couldn't find any author by that name.";
			}

			if(authors.size() > 1){
				storedEvents.put(data.getSender(), authors);
				StringBuilder str = new StringBuilder();
				str.append("Did you mean: ");
				String prepend = "";
				for (Selectable author : authors) {
					str.append(prepend);
					prepend=",";
					str.append(Colors.BOLD);
					str.append(((Author)author).getAuthor());
					str.append(Colors.NORMAL);
				}
				str.append("?");
				return str.toString();
			}else{
				return getAuthorDetailsPages(((Author)authors.get(0)).getAuthor());
			}
			
			
			
		}catch(Exception e){
			logger.error("Error constructing author detail",e);
		}
		
		return "I apologize, there's been an error.  Please inform DrMagnus there's an error with author details.";
	}

	public static String disambiguateWikipedia(CommandData data, List<String> titles){
		if(titles.isEmpty()){
			return "I couldn't find any choices.";
		}
		try{
				ArrayList<Selectable> choices = new ArrayList<>();
				for(String title : titles){
					choices.add(new WikipediaAmbiguous(data, title));
				}
				storedEvents.put(data.getSender(), choices);
				StringBuilder str = new StringBuilder();
				str.append("Did you mean: ");
				String prepend = "";
				for (Selectable choice : choices) {
					str.append(prepend);
					prepend=",";
					str.append(Colors.BOLD);
					str.append(((WikipediaAmbiguous)choice).getTitle());
					str.append(Colors.NORMAL);
				}
				str.append("?");
				String result = str.toString();
				result = result.substring(0, Math.min(400, result.length()));
				int lastComma = result.lastIndexOf(',');
				if(lastComma != -1){
					result = result.substring(0, lastComma);
				}
				return result;
		}catch(Exception e){
			logger.error("Error constructing choice",e);
		}

		return "I apologize, there's been an error.  Please inform DrMagnus there's an error with author details.";
	}
	
	public static String getAuthorDetailsPages(String user){
		String lowerUser = user.toLowerCase();
		Timestamp ts = new java.sql.Timestamp(0l);
		int rating = 0;
		Page latest = null;
		int pagecount = 0;
		Page authorPage = null;
		try{
		CloseableStatement stmt = Connector.getStatement(Queries.getQuery("findAuthorPage"), lowerUser);
		ResultSet rs = stmt.getResultSet();
		if(rs != null && rs.next()){
			authorPage = new Page(rs.getString("pagename"),
                    rs.getString("title"),
                    rs.getInt("rating"),
                    rs.getString("created_by"),
                    rs.getTimestamp("created_on"),
                    rs.getBoolean("scppage"),
                    rs.getString("scptitle"));

			rating = rating + authorPage.getRating();
			pagecount++;
			if(authorPage.getCreatedAt() != null && authorPage.getCreatedAt().compareTo(ts) > 0){
				ts = authorPage.getCreatedAt();
				 latest = authorPage;
			}

		}
		rs.close();
		stmt.close();
		
		ArrayList<Page> pages = new ArrayList<>();
		stmt = Connector.getStatement(Queries.getQuery("findAllSkips"), lowerUser, lowerUser);
		rs = stmt.getResultSet();
		while(rs != null && rs.next()){
			Page p = new Page(rs.getString("pagename"),
					rs.getString("title"),
					rs.getInt("rating"),
					rs.getString("created_by"),
					rs.getTimestamp("created_on"),
					rs.getBoolean("scppage"),
					rs.getString("scptitle"));
			pages.add(p);
			pagecount++;
			rating = rating + p.getRating();

			if(p.getCreatedAt() != null && p.getCreatedAt().compareTo(ts) > 0){
				ts = p.getCreatedAt();
				latest = p;
			}
		}
		
		stmt = Connector.getStatement(Queries.getQuery("findAllTales"), lowerUser, lowerUser);
		rs = stmt.getResultSet();
		List<Page> tales = new LinkedList<>();
		while(rs != null && rs.next()){
			Page p = new Page(rs.getString("pagename"),
					rs.getString("title"),
					rs.getInt("rating"),
					rs.getString("created_by"),
					rs.getTimestamp("created_on"),
					rs.getBoolean("scppage"),
					rs.getString("scptitle"));

			rating = rating + p.getRating();
			pagecount++;
			tales.add(p);

			if(p.getCreatedAt() != null && p.getCreatedAt().compareTo(ts) > 0){
				ts = p.getCreatedAt();
				latest = p;
			}
		}


		stmt = Connector.getStatement(Queries.getQuery("findAllGoi"), lowerUser, lowerUser);
		rs = stmt.getResultSet();
		List<Page> gois = new LinkedList<>();
		while(rs != null && rs.next()){
			Page p = new Page(rs.getString("pagename"),
					rs.getString("title"),
					rs.getInt("rating"),
					rs.getString("created_by"),
					rs.getTimestamp("created_on"),
					rs.getBoolean("scppage"),
					rs.getString("scptitle"));
			gois.add(p);
			pagecount++;
			rating = rating + p.getRating();

			if(p.getCreatedAt() != null && p.getCreatedAt().compareTo(ts) > 0){
				ts = p.getCreatedAt();
				latest = p;
			}
		}

			stmt = Connector.getStatement(Queries.getQuery("findAllOthers"), lowerUser, lowerUser);
			rs = stmt.getResultSet();
			List<Page> others = new LinkedList<>();
			while(rs != null && rs.next()){
				Page p = new Page(rs.getString("pagename"),
						rs.getString("title"),
						rs.getInt("rating"),
						rs.getString("created_by"),
						rs.getTimestamp("created_on"),
						rs.getBoolean("scppage"),
						rs.getString("scptitle"));
				others.add(p);
				pagecount++;
				rating = rating + p.getRating();
				if(p.getCreatedAt() != null && p.getCreatedAt().compareTo(ts) > 0){
					ts = p.getCreatedAt();
					latest = p;
				}

			}
		
		StringBuilder str = new StringBuilder();
		str.append(Colors.BOLD);
		str.append(user);
		str.append(Colors.NORMAL);
		str.append(" - ");
		
		if(authorPage != null){
			str.append("(");
			str.append("http://www.scp-wiki.net/");
			str.append(authorPage.getPageLink());
			str.append(") ");
		}

		str.append("has ");
		str.append(Colors.BOLD);
		str.append(pagecount);
		str.append(Colors.NORMAL);
		str.append(" pages. (");
		str.append(Colors.BOLD);
		str.append(pages.size());
		str.append(Colors.NORMAL);
		str.append(" SCP articles, ");
		str.append(Colors.BOLD);
		str.append(tales.size());
		str.append(Colors.NORMAL);
		str.append(" Tales, ");
		str.append(Colors.BOLD);
		str.append(gois.size());
		str.append(Colors.NORMAL);
		str.append(" GoI Formats, ");
		str.append(Colors.BOLD);
		str.append(others.size());
		str.append(Colors.NORMAL);
		str.append(" others.)");
		str.append(" They have ");
		str.append(Colors.BOLD);
		str.append(rating);
		str.append(Colors.NORMAL);
		str.append(" net upvotes with an average of ");
		str.append(Colors.BOLD);
		long avg = Math.round((rating) / (pagecount));
		str.append(avg);
		str.append(Colors.NORMAL);
		str.append(".  Their latest page is ");
		str.append(Colors.BOLD);
		if(latest.getScpPage()){
			str.append(latest.getTitle());
			str.append(": ");
			str.append(latest.getScpTitle());
		}else{
			str.append(latest.getTitle());
		}
		str.append(Colors.NORMAL);
		str.append(" at ");
		str.append(Colors.BOLD);
		str.append(latest.getRating());
		str.append(Colors.NORMAL);
		str.append(".");
		
		return str.toString();
		} catch (Exception e){
			logger.error("There was an exception retreiving author pages stuff",e);
		}
		
		return "I'm sorry there was some kind of error";
	}

	public static String getPotentialTargets(String[] terms, String username) {
		Boolean exact = terms[1].equalsIgnoreCase("-e");
		int indexOffset = 1;
		if(exact){
			indexOffset = 2;
		}
		ArrayList<Selectable> potentialPages = new ArrayList<Selectable>();
		String[] lowerterms = new String[terms.length - indexOffset];
		for (int i = indexOffset ; i < terms.length; i++) {
			lowerterms[i - indexOffset] = terms[i].toLowerCase();
			logger.info(lowerterms[i - indexOffset]);
		}
		try {
			ResultSet rs = null;
			CloseableStatement stmt = null;
			PreparedStatement state = null;
			Connection conn = null;
			if(exact){
				stmt = Connector.getArrayStatement(
						Queries.getQuery("findskips"), lowerterms);
				logger.info(stmt.toString());
				rs = stmt.getResultSet();
			}else{
				String query = "select pagename,title,scptitle,scppage from pages where";
				for(int j = indexOffset; j < terms.length; j++){
					if(j != indexOffset){
						query +=" and";
					}
					query += " lower(coalesce(scptitle, title)) like ?";
				}
				conn = Connector.getConnection();
				state = conn.prepareStatement(query);
				for(int j = indexOffset; j < terms.length; j++){
					state.setString(j - (indexOffset - 1), "%"+terms[j].toLowerCase()+"%");
				}
				logger.info(state.toString());
				rs = state.executeQuery();
			}
			while (rs != null && rs.next()) {
				potentialPages.add(new Page(rs.getString("pagename"), rs
						.getString("title"), rs.getBoolean("scppage"), rs
						.getString("scptitle")));
			}
			if(stmt != null){
				stmt.close();
			}
			
			if(conn != null){
				conn.close();
			}
			if(rs != null){
				rs.close();
			}
		} catch (SQLException e) {
			logger.error("There was an issue grabbing potential SCP pages", e);
		}

		if (potentialPages.size() > 1) {
			storedEvents.put(username, potentialPages);
			StringBuilder str = new StringBuilder();
			str.append("Did you mean : ");

			for (Selectable p : potentialPages) {
				Page page = (Page)p; 
				str.append(Colors.BOLD);
				str.append(page.getScpPage() ? page.getTitle()
						+ ": " + page.getScpTitle()  : page.getTitle());
				str.append(Colors.NORMAL);
				str.append(", ");
			}
			str.append("?");
			return str.toString();
		} else {
			if (potentialPages.size() == 1) {
				return getPageInfo(((Page)potentialPages.get(0)).getPageLink());
			} else {
				return Command.NOT_FOUND;
			}
		}
	}

	public static String getStoredInfo(String index, String username) {
		try {
			Selectable s = storedEvents.get(username).get(Integer.parseInt(index) - 1);
			if(s instanceof Page){
				return getPageInfo((String)s.selectResource());
			}else if(s instanceof Author){
				return getAuthorDetailsPages((String)s.selectResource());
			}else if(s instanceof WikipediaAmbiguous){
				WikipediaAmbiguous choice = (WikipediaAmbiguous) s;
				CommandData data = choice.getData();
				return WikipediaSearch.search(data, choice.getTitle());
			}
			
		} catch (Exception e) {
			logger.error("There was an exception getting stored info", e);
		}
 
		return "Either the command was malformed, or I have nothing for you to get.";
	}

	
	public static String findTime(Long time){
		//compensate for EST (helen runs in EST)
		time = (System.currentTimeMillis() + HOURS * 4) - time;
		Long diff = 0l;
		if(time >= YEARS){
			diff = time/YEARS;
			return (time / YEARS) + " year" + (diff > 1 ? "s" : "") + " ago ";
			
		}else if( time >= DAYS){
			diff = time/DAYS;
			return (time / DAYS) + " day" + (diff > 1 ? "s" : "") + " ago ";
			
		}else if(time >= HOURS){
			diff = (time/HOURS);
			return (time / HOURS) + " hour" + (diff > 1 ? "s" : "") + " ago ";
			
		}else if( time >= MINUTES){
			diff = time/MINUTES;
			return (time / MINUTES) + " minute" + (diff > 1 ? "s" : "") + " ago ";
			
		}else{
			return "A few seconds ago ";
		}
	
	}
	
	
		
	
	
	
	
	
	
	
	
	
	
	
	
	
}
