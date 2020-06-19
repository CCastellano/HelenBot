package com.helen.database.data;

import com.helen.commands.Command;
import com.helen.commands.CommandData;
import com.helen.database.Selectable;
import com.helen.database.framework.*;
import com.helen.search.WikipediaAmbiguous;
import com.helen.search.WikipediaSearch;
import com.helen.search.XmlRpcTypeNil;
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
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class Pages {


    private static final Long YEARS = 1000 * 60 * 60 * 24 * 365L;
    private static final Long DAYS = 1000 * 60 * 60 * 24L;
    private static final Long HOURS = 1000 * 60L * 60;
    private static final Long MINUTES = 1000 * 60L;
    private static final char ZERO_WIDTH_SPACE = '\u200B';
    private static final Logger logger = Logger.getLogger(Pages.class);
    private static XmlRpcClientConfigImpl config;
    private static XmlRpcClient client;
    private static Long lastLc = System.currentTimeMillis() - 20000;
    private static HashMap<String, ArrayList<Selectable>> storedEvents = new HashMap<>();
    private static boolean setupComplete = false;

    static {
        config = new XmlRpcClientConfigImpl();
        try {
            Optional<Config> wikidotServer = Configs.getSingleProperty(
                    "wikidotServer");
            Optional<Config> appName = Configs.getSingleProperty("appName");
            Optional<Config> wikidotApiKey = Configs.getSingleProperty("wikidotapikey");
            if (wikidotApiKey.isPresent() && wikidotServer.isPresent() && appName.isPresent()) {
                config.setServerURL(new URL(wikidotServer.get().getValue()));
                config.setBasicUserName(appName.get().getValue());
                config.setBasicPassword(wikidotApiKey.get().getValue());
                config.setEnabledForExceptions(true);
                config.setConnectionTimeout(10 * 1000);
                config.setReplyTimeout(30 * 1000);

                client = new XmlRpcClient();
                client.setTransportFactory(new XmlRpcSun15HttpTransportFactory(
                        client));
                client.setTypeFactory(new XmlRpcTypeNil(client));
                client.setConfig(config);
                setupComplete = true;
            }


        } catch (Exception e) {
            logger.error("There was an exception", e);
        }

    }

    private static Object pushToAPI(String method, Object... params)
            throws XmlRpcException {
        return client.execute(method, params);
    }

    public static Optional<ArrayList<String>> lastCreated() {
        if (!setupComplete) {
            return Optional.empty();
        }
            ArrayList<String> pagelist = new ArrayList<>();
            try {
                String regex = "<td style=\"vertical-align: top;\"><a href=\"\\/(.+)\">(.+)<\\/a><\\/td>";
                Pattern r = Pattern.compile(regex);
                String s;
                URL u = new URL("http://www.scp-wiki.wikidot.com/most-recently-created");
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
            return Optional.of(pagelist);

    }


    public static String getUnused(CommandData data) {
        String[] messageParts = data.getSplitMessage();


        List<String> flags = Arrays.stream(messageParts).filter(s -> s.contains("-")).collect(Collectors.toList());
        List<String> slots = new ArrayList<>();
        int min;
        int max;
        if (flags.contains("-s") || flags.contains("-series")) {
            if (flags.size() == 1) {
                flags.add("-r");
            }
            if (messageParts.length > 2) {
                String seriesNumber = messageParts[2];
                if (seriesNumber.matches("[1-6]")) {
                    Integer i = Integer.parseInt(seriesNumber);
                    if (i == 1) {
                        min = 1;
                        max = 999;
                    } else {
                        min = 1000 * (i - 1);
                        max = (1000 * i) - 1;
                    }
                } else {
                    return "When using series flags, please enter the series number between 1 and 6 immediately after.  e.g. .unused -s 1 -c";
                }
            } else {
                min = 1;
                max = 5999;
                flags.add("-r");
            }
        } else {
            min = 1;
            max = 5999;
            flags.add("-r");
        }

        try (CloseableStatement stmt = Connector.getStatement(Queries.getQuery("unusedBySeries"), min, max)) {
            if (stmt != null) {
                ResultSet rs = stmt.executeQuery();
                while (rs != null && rs.next()) {
                    slots.add(rs.getString("missing_id"));
                }
            }
        } catch (SQLException e) {
            logger.error(e);
        }

        if (flags.contains("-c")) {
            return "There are " + slots.size() + " unused slots between " + min + " and " + max + ".";
        }

        if (flags.contains("-l")) {
            if (!slots.isEmpty()) {
                return "The most recent unused slot is: http://scp-wiki.wikidot.com/scp-" + slots.get(slots.size() - 1);
            } else {
                return "There are no unused slots between " + min + " and " + max + ".";
            }
        }

        if (flags.contains("-f")) {
            if (!slots.isEmpty()) {
                return "The least recent unused slot is: http://scp-wiki.wikidot.com/scp-" + slots.get(0);
            } else {
                return "There are no unused slots between " + min + " and " + max + ".";
            }
        }

        if (flags.contains("-r")) {
            if (!slots.isEmpty()) {
                return "http://scp-wiki.wikidot.com/scp-" + slots.get(new Random().nextInt(slots.size()));
            } else {
                return "There are no unused slots between " + min + " and " + max + ".";
            }
        }


        return "";
    }

    private static String getTitle(String pagename) {
        String pageName = null;
        try {
            CloseableStatement stmt = Connector.getStatement(
                    Queries.getQuery("getPageByName"), pagename);
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

    /* Avoid pinging authors on IRC when their pages are mentioned
     * by inserting a zero-width space.
     *
     * Not worth using when the input command specifies an author,
     * because in that case the user will get pinged anyway and we
     * can't do anything about it.
     */

    private static String getNonPingAuthorName(String authorname) {

        StringBuilder builder = new StringBuilder();
        builder.append(authorname.charAt(0));
        builder.append(ZERO_WIDTH_SPACE);
        builder.append(authorname.substring(1, authorname.length()-1));

        return builder.toString();
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
        Map<String, Object> params = new HashMap<String, Object>();
        Optional<Config> site = Configs.getSingleProperty("site");
        if (!site.isPresent()) {
            return Command.CONFIG_ERROR;
        }
        params.put("site", site.get().getValue());
        String[] target = new String[]{targetName.toLowerCase()};
        params.put("pages", target);
        ArrayList<String> keyswewant = new ArrayList<>();
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
            if (ratingEnabled) {
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

            CloseableStatement stmt = Connector.getStatement(Queries.getQuery("findRewrite"), targetName);
            ResultSet rs = stmt.getResultSet();
            Metadata meta = null;
            List<Metadata> finalMetas = new LinkedList<>();
            while (rs != null && rs.next()) {
                Metadata m = new Metadata(rs.getString("pagename"),
                        rs.getString("username"),
                        rs.getString("metadata_type"),
                        rs.getString("authorage_date"));

                LocalDate newDate = LocalDate.parse(m.getDate());
                if (meta == null || LocalDate.parse(meta.getDate()).compareTo(newDate) < 0) {
                    meta = m;
                } else if (LocalDate.parse(meta.getDate()).compareTo(newDate) == 0) {
                    finalMetas.clear();
                    finalMetas.add(meta);
                    finalMetas.add(m);
                }
            }

            if (finalMetas.isEmpty() && meta != null) {
                finalMetas.add(meta);
            }

            stmt = Connector.getStatement(Queries.getQuery("findAuthors"), targetName);
            rs = stmt.getResultSet();
            List<Metadata> authorFinalMetas = new LinkedList<>();
            while (rs != null && rs.next()) {
                Metadata m = new Metadata(rs.getString("pagename"),
                        rs.getString("username"),
                        rs.getString("metadata_type"),
                        rs.getString("authorage_date"));

                authorFinalMetas.add(m);
            }

            returnString.append("By: ");
            if (!authorFinalMetas.isEmpty()) {

                if (authorFinalMetas.size() == 1) {
                    returnString.append(getNonPingAuthorName(authorFinalMetas.get(0).getUsername()));
                } else if (authorFinalMetas.size() == 2) {
                    returnString.append(getNonPingAuthorName(authorFinalMetas.get(0).getUsername()))
                        .append(" and ")
                        .append(getNonPingAuthorName(authorFinalMetas.get(1).getUsername()));
                } else {
                    returnString.append(authorFinalMetas.stream().map(Metadata::getUsername).map(Pages::getNonPingAuthorName).collect(Collectors.joining(", ")));
                }
            } else {

                returnString.append(result.get(targetName).get("created_by"));
            }
            if (meta != null) {

                returnString.append(" rewritten on: ");
                returnString.append(meta.getDate());
                returnString.append(" by ");
                if (finalMetas.size() == 1) {
                    returnString.append(getNonPingAuthorName(finalMetas.get(0).getUsername()));
                } else if (finalMetas.size() == 2) {
                    returnString.append(getNonPingAuthorName(finalMetas.get(0).getUsername()))
                        .append(" and ")
                        .append(getNonPingAuthorName(finalMetas.get(1).getUsername()));
                } else {
                    returnString.append(finalMetas.stream().map(Metadata::getUsername).map(Pages::getNonPingAuthorName).collect(Collectors.joining(", ")));
                }
            }
            returnString.append(")");
            returnString.append(" - ");
            returnString.append("http://scp-wiki.wikidot.com/");
            returnString.append(targetName);

            return returnString.toString();

        } catch (Exception e) {
            logger.error("There was an exception retreiving metadata", e);
        }

        return Command.NOT_FOUND;
    }

    public static String getAuthorDetail(CommandData data, String user) {
        user = user.toLowerCase();
        user = user.trim();
        try {

            ArrayList<Selectable> authors = new ArrayList<>();
            CloseableStatement stmt = Connector.getStatement(Queries.getQuery("findAuthorName"), "%" + user + "%","%" + user + "%","%" + user + "%");
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

        return "I apologize, there's been an error.  Please inform DrMagnus there's an error with author details.";
    }

    private static Page getAuthorPage(String user) {
        Page p = null;
        CloseableStatement authorStatement;
        try (CloseableStatement authorOverrideStatement = Connector.getStatement(Queries.getQuery("findAuthorOverride"), user)) {
            try (ResultSet rs = authorOverrideStatement.getResultSet()) {
                if (rs != null && rs.next()) {
                    String overridenUrl = rs.getString("url");
                    authorStatement = Connector.getStatement(Queries.getQuery("getOverridenAuthorPage"), overridenUrl.toLowerCase());
                } else {
                    authorStatement = Connector.getStatement(Queries.getQuery("findAuthorPage"), user.toLowerCase());
                }
            } catch (Exception e) {
                logger.error("There was an exception attempting to retrieve an override for user: " + user, e);
                return p;
            }
        }

        try (ResultSet authorRs = authorStatement.getResultSet()) {
            if (authorRs != null && authorRs.next()) {
                p = new Page(authorRs.getString("pagename"),
                        authorRs.getString("title"),
                        authorRs.getInt("rating"),
                        authorRs.getString("created_by"),
                        authorRs.getTimestamp("created_on"),
                        authorRs.getBoolean("scppage"),
                        authorRs.getString("scptitle"));
            }

        } catch (Exception e) {
            logger.error("There was an exception attempting to retrieve an override for user: " + user, e);
        }


        return p;
    }

    private static List<Page> getPageList(String username, String queryName) {
        List<Page> skips = new ArrayList<>();
        try (CloseableStatement stmt = Connector.getStatement(Queries.getQuery(queryName), username, username)) {
            try (ResultSet rs = stmt.getResultSet()) {
                while (rs != null && rs.next()) {
                    skips.add(new Page(rs.getString("pagename"),
                            rs.getString("title"),
                            rs.getInt("rating"),
                            rs.getString("created_by"),
                            rs.getTimestamp("created_on"),
                            rs.getBoolean("scppage"),
                            rs.getString("scptitle")));
                }
            }

        } catch (SQLException e) {
            logger.error("There was an exception attempting to retreive a page.", e);
        }


        return skips;
    }


    public static String getAuthorDetailsPages(String user) {
        String lowerUser = user.toLowerCase();
        try {
            List<Page> pages = new ArrayList<>(getPageList(lowerUser, "findAllSkips"));
            int skips = pages.size();
            pages.addAll(getPageList(lowerUser, "findAllTales"));
            int tales = pages.size() - skips;
            pages.addAll(getPageList(lowerUser, "findAllGoi"));
            int gois = pages.size() - (skips + tales);
            pages.addAll(getPageList(lowerUser, "findAllOthers"));
            Page authorPage = getAuthorPage(lowerUser);
            if (authorPage != null) {
                pages.add(authorPage);
            }
            int others = pages.size() - (skips + tales + gois);

            Optional<Page> latest = pages.stream().max(Comparator.comparing(Page::getCreatedAt));
            StringBuilder str = new StringBuilder();
            str.append(Colors.BOLD);
            str.append(user);
            str.append(Colors.NORMAL);
            str.append(" - ");

            if (authorPage != null) {
                str.append("(");
                str.append("http://www.scp-wiki.wikidot.com/");
                str.append(authorPage.getPageLink());
                str.append(") ");
            }

            str.append("has ");
            str.append(Colors.BOLD);
            str.append(pages.size());
            str.append(Colors.NORMAL);
            str.append(" pages. (");
            str.append(Colors.BOLD);
            str.append(skips);
            str.append(Colors.NORMAL);
            str.append(" SCP articles, ");
            str.append(Colors.BOLD);
            str.append(tales);
            str.append(Colors.NORMAL);
            str.append(" Tales, ");
            str.append(Colors.BOLD);
            str.append(gois);
            str.append(Colors.NORMAL);
            str.append(" GoI Formats, ");
            str.append(Colors.BOLD);
            str.append(others);
            str.append(Colors.NORMAL);
            str.append(" others.)");
            str.append(" They have ");
            str.append(Colors.BOLD);
            str.append(pages.stream().mapToInt(Page::getRating).sum());
            str.append(Colors.NORMAL);
            str.append(" net upvotes with a rounded average of ");
            str.append(Colors.BOLD);
            if (pages.size() > 0) {
                long avg = Math.round((double) pages.stream().mapToInt(Page::getRating).sum() / pages.size());
                str.append(avg);
            } else {
                str.append("apparently zero? (contact magnus.) ");
            }
            str.append(Colors.NORMAL);
            str.append(".  Their latest page is ");
            str.append(Colors.BOLD);
            if (latest.isPresent()) {
                if (latest.get().getScpPage()) {
                    str.append(latest.get().getTitle());
                    str.append(": ");
                    str.append(latest.get().getScpTitle());
                } else {
                    str.append(latest.get().getTitle());
                }
                str.append(Colors.NORMAL);
                str.append(" at ");
                str.append(Colors.BOLD);
                str.append(latest.get().getRating());
                str.append(Colors.NORMAL);
                str.append(".");
            }


            return str.toString();
        } catch (Exception e) {
            logger.error("There was an exception retreiving author pages stuff", e);
        }

        return "I'm sorry there was some kind of error";
    }

    public static String getPotentialTargets(String[] terms, String username) {
        boolean exact = terms[1].equalsIgnoreCase("-e");
        int indexOffset = 1;
        if (exact) {
            indexOffset = 2;
        }
        ArrayList<Selectable> potentialPages = new ArrayList<>();
        String[] lowerterms = new String[terms.length - indexOffset];
        for (int i = indexOffset; i < terms.length; i++) {
            lowerterms[i - indexOffset] = terms[i].toLowerCase();
        }
        try {
            ResultSet rs;
            CloseableStatement stmt = null;
            PreparedStatement state;
            Connection conn = null;
            if (exact) {
                stmt = Connector.getArrayStatement(
                        Queries.getQuery("findskips"), lowerterms);
                rs = stmt.getResultSet();
            } else {
                String query = "select pagename,title,scptitle,scppage from pages where";
                for (int j = indexOffset; j < terms.length; j++) {
                    if (j != indexOffset) {
                        query += " and";
                    }
                    query += " lower(coalesce(scptitle, title)) like ?";
                }
                conn = Connector.getConnection();
                state = conn.prepareStatement(query);
                for (int j = indexOffset; j < terms.length; j++) {
                    state.setString(j - (indexOffset - 1), "%" + terms[j].toLowerCase() + "%");
                }
                rs = state.executeQuery();
            }
            while (rs != null && rs.next()) {
                potentialPages.add(new Page(rs.getString("pagename"), rs
                        .getString("title"), rs.getBoolean("scppage"), rs
                        .getString("scptitle")));
            }
            if (stmt != null) {
                stmt.close();
            }

            if (conn != null) {
                conn.close();
            }
            if (rs != null) {
                rs.close();
            }
        } catch (SQLException e) {
            logger.error("There was an issue grabbing potential SCP pages", e);
        }

        if (potentialPages.size() > 1) {
            storedEvents.put(username, potentialPages);
            StringBuilder str = new StringBuilder();
            str.append("Did you mean : ");
            int i = 1;
            for (Selectable p : potentialPages) {
                Page page = (Page) p;
                str.append(Colors.BOLD);
                str.append(i++).append(") ");
                str.append(Colors.NORMAL);
                str.append(page.getScpPage() ? page.getTitle() + ": " +  page.getScpTitle() : page.getTitle());
                str.append(", ");
            }
            str.append("?");
            return str.toString();
        } else {
            if (potentialPages.size() == 1) {
                return getPageInfo(((Page) potentialPages.get(0)).getPageLink());
            } else {
                return Command.NOT_FOUND;
            }
        }
    }

    public static String getStoredInfo(String index, String username) {
        try {
            Selectable s = storedEvents.get(username).remove(Integer.parseInt(index) - 1);
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


    public static String findTime(Long time) {
        //compensate for EST (helen runs in EST)
        time = (System.currentTimeMillis() + HOURS * 4) - time;
        long diff;
        if (time >= YEARS) {
            diff = time / YEARS;
            return (time / YEARS) + " year" + (diff > 1 ? "s" : "") + " ago ";

        } else if (time >= DAYS) {
            diff = time / DAYS;
            return (time / DAYS) + " day" + (diff > 1 ? "s" : "") + " ago ";

        } else if (time >= HOURS) {
            diff = (time / HOURS);
            return (time / HOURS) + " hour" + (diff > 1 ? "s" : "") + " ago ";

        } else if (time >= MINUTES) {
            diff = time / MINUTES;
            return (time / MINUTES) + " minute" + (diff > 1 ? "s" : "") + " ago ";

        } else {
            return "A few seconds ago ";
        }

    }


}
