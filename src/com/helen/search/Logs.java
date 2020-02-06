package com.helen.search;

import com.helen.database.framework.CloseableStatement;
import com.helen.database.framework.Connector;
import com.helen.database.framework.Queries;
import org.apache.log4j.Logger;

import java.sql.ResultSet;
import java.time.format.DateTimeFormatter;

public class Logs {

    private static final Logger logger = Logger.getLogger("Logs");

    private static DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public static String getPasteForTimeRangeAndChannel(String channel, String start, String end) {
        try {
            formatter.parse(start);
            formatter.parse(end);
            StringBuilder str = new StringBuilder();
            try (CloseableStatement stmt = Connector.getStatement(Queries.getQuery("searchLogs"), channel, start, end)) {
                try (ResultSet rs = stmt != null ? stmt.getResultSet() : null) {
                    while (rs != null && rs.next()) {
                        str.append(rs.getString("timestamp")).append(" ").append(rs.getString("username"))
                                .append(": ").append(rs.getString("message")).append("\n");
                    }
                }
            }
            return PastebinUtils.getPasteForLog(str.toString(), "Requested Log");
        } catch (Exception e) {
            logger.error("Issue with the date: " + channel + " " + start + " " + end, e);
            return "There was a problem making the call.  Consult with my developers for how to use this function.";
        }
    }

    public static String getFormattedPasteForTimeRangeAndChannel(String channel, String start, String end, String usernameToHighlight) {
        try {
            formatter.parse(start);
            formatter.parse(end);
            StringBuilder str = new StringBuilder();
            try (CloseableStatement stmt = Connector.getStatement(Queries.getQuery("searchLogs"), channel, start, end)) {
                try (ResultSet rs = stmt != null ? stmt.getResultSet() : null) {
                    while (rs != null && rs.next()) {

                        str.append("> ").append(rs.getString("timestamp")).append(" ");
                        String user = rs.getString("username");
                        if (user.equalsIgnoreCase(usernameToHighlight)) {
                            str.append("**##red|").append(user).append("##").append(": ").append(rs.getString("message")).append("**\n");
                        } else {
                            str.append(rs.getString("username"))
                                    .append(": ").append(rs.getString("message")).append("\n");
                        }
                    }
                }
            }
            return PastebinUtils.getPasteForLog(str.toString(), "Requested Log");
        } catch (Exception e) {
            logger.error("Issue with the date: " + channel + " " + start + " " + end, e);
            return "There was a problem making the call.  Consult with my developers for how to use this function.";
        }
    }
}
