package com.helen.database.users;


import com.helen.commands.Command;
import com.helen.commands.CommandData;
import com.helen.commands.CommandResponse;
import com.helen.database.framework.CloseableStatement;
import com.helen.database.framework.Connector;
import com.helen.database.framework.Queries;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.jibble.pircbot.Colors;

import java.io.IOException;
import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Bans {

    private static final Logger logger = Logger.getLogger(Bans.class);
    private static ConcurrentHashMap<String, HashSet<BanInfo>> bans = new ConcurrentHashMap<>();
    private static Map<String, BanPrep> additionConfirmations = new ConcurrentHashMap<>();
    private static Map<String, Integer> deletionConfirmations = new ConcurrentHashMap<>();
    public static DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public static void updateBans() throws IOException {
        bans.clear();

        try {
            CloseableStatement stmt = Connector.getStatement(Queries.getQuery("loadBans"));
            ResultSet rs = stmt.getResultSet();
            Map<Integer, BanInfo> infoMap = new ConcurrentHashMap<>();

            while (rs != null && rs.next()) {
                int banId = rs.getInt("banid");
                String reason = rs.getString("reason");
                String duration = rs.getString("duration");
                String thread = rs.getString("thread");
                String value = rs.getString("value");
                String channel = rs.getString("channel");
                infoMap.computeIfAbsent(banId, i -> new BanInfo(reason, duration, thread, channel));
                String type = rs.getString("type");
                switch (type) {
                    case "username":
                        infoMap.get(banId).addUsername(value.toLowerCase());
                        break;
                    case "hostmask":
                        infoMap.get(banId).addHostmask(value.toLowerCase());
                        if(value.contains("@") || value.contains("*")){
                            infoMap.get(banId).setSpecial(true);
                        }
                        break;
                }

            }
            Map<String, HashSet<BanInfo>> completeMaps = new ConcurrentHashMap<>();
            for (Integer i : infoMap.keySet()) {
                BanInfo info = infoMap.get(i);
                completeMaps.computeIfAbsent(info.getChannel(), channel -> new HashSet<>());
                completeMaps.get(info.getChannel()).add(info);
            }
            bans.put("#ethicscommittee", completeMaps.get("#site19"));
            bans.put("#site19", completeMaps.get("#site19"));
            bans.put("#thecritters", completeMaps.get("#site19"));
            bans.put("#workshop", completeMaps.get("#site19"));
            bans.put("#site20", completeMaps.get("#site19"));
            bans.put("#site17", completeMaps.get("#site17"));
        }catch(Exception e){
            logger.error("There was an exception pulling bans from the database.",e);
        }

    }


    public static boolean getSuperUserBan(String username, String hostmask, String login) {
        return getUserBan(username, hostmask, "#site19", login) != null &&
                getUserBan(username, hostmask, "#site17", login) != null;
    }

    public static BanInfo getUserBan(String username, String hostmask, String channel, String login) {
        LocalDateTime today = LocalDateTime.now();
        if (bans.containsKey(channel)) {
            for (BanInfo info : bans.get(channel)) {
                if ((info.getHostmasks().contains(hostmask) || info.getUserNames().contains(username)) && info.getDuration().isAfter(today)) {
                    return info;
                } else if (info.isSpecial()) {
                    for (String infoHostMask : info.getHostmasks()) {
                        if (infoHostMask.contains("@")) {
                            if (infoHostMask.contains("@*")) {
                                if (infoHostMask.split("@")[0].equalsIgnoreCase(login)) {
                                    return info;
                                }
                            }
                            if (infoHostMask.equalsIgnoreCase(login + "@" + hostmask)) {
                                return info;
                            }
                        } else if (infoHostMask.contains("*")) {
                            if (isMatch(hostmask, infoHostMask)) {
                                return info;
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    private static boolean isMatch(String s, String p) {
        int i = 0;
        int j = 0;
        int starIndex = -1;
        int iIndex = -1;

        while (i < s.length()) {
            if (j < p.length() && (p.charAt(j) == '?' || p.charAt(j) == s.charAt(i))) {
                ++i;
                ++j;
            } else if (j < p.length() && p.charAt(j) == '*') {
                starIndex = j;
                iIndex = i;
                j++;
            } else if (starIndex != -1) {
                j = starIndex + 1;
                i = iIndex + 1;
                iIndex++;
            } else {
                return false;
            }
        }
        while (j < p.length() && p.charAt(j) == '*') {
            ++j;
        }
        return j == p.length();
    }

    public static String enactDeleteBan(String username){
        Integer id = deletionConfirmations.remove(username);
        if(id == null){
            return "You have no pending ban confirmations.";
        }else {
            try (CloseableStatement deleteStatement = Connector.getStatement(Queries.getQuery("deleteBan"), id)) {
                if (deleteStatement != null) {
                    if (deleteStatement.executeDelete()) {
                        updateBans();
                        return "Deleted all ban usernames, hostmasks, and entries for banid: " + id;
                    }
                } else {
                    return "Something went wrong trying to construct the delete query.  Magnus, a word?";
                }
            } catch (Exception e) {
                logger.error(e);
                return "Something went wrong attempting to delete the ban.  Magnus, a word?";
            }
            return "I believe something went wrong, but I'm not sure. Magnus?";
        }
    }

    public static String enactConfirmedBan(String username) {
        BanPrep prep = additionConfirmations.remove(username);
        if(prep == null){
            return "You have no pending ban confirmations.";
        }else {
                try(CloseableStatement stmt = Connector.getStatement(Queries.getQuery("addBan"), prep.getReason(), prep.getChannel(), prep.getThread() == null ? "" : prep.getThread(), prep.getEndTime().format(timeFormatter))) {
                    ResultSet rs = stmt.execute();
                    Integer value = (rs != null && rs.next()) ? rs.getInt("banid") : -1;
                    for (String user : prep.getUsers()) {
                        CloseableStatement usernamestmt = Connector.getStatement(Queries.getQuery("addUsernameBan"), value, user);
                        usernamestmt.executeUpdate();
                    }
                    for (String hostmask : prep.getHostmasks()) {
                        CloseableStatement hostmaskstmt = Connector.getStatement(Queries.getQuery("addHostmaskBan"), value, hostmask);
                        hostmaskstmt.executeUpdate();
                    }
                    updateBans();
                }catch(Exception e){
                    logger.error("There was an exception enacting a ban.",e);
                    return Command.ERROR;
                }

            return "Ban enacted for preparation: " + prep.getResponse();
        }
    }

    public static List<String> queryBan(CommandData data) {
        BanPrep prep;
        try{
            prep = new BanPrep(data);
        }catch(Exception e){
            logger.error("Exception making chat ban prep:",e);
            return Collections.singletonList("You must specify at least -u or -h to search for a ban.");
        }
        if(prep.getFlagSet().contains("u") && prep.getFlagSet().contains("h")){
            return Collections.singletonList("You should specify EITHER -u or -h to search for a ban.");
        }
        String searchTerm = prep.getFlagSet().contains("u") ? "%" + prep.getUsers().get(0).toLowerCase() + "%" : "%" + prep.getHostmasks().get(0).toLowerCase() + "%";
        Map<Integer, BanInfo> returnInfo = new HashMap<>();
        try(CloseableStatement stmt = prep.getFlagSet().contains("u") ? Connector.getStatement(Queries.getQuery("findBanByUsername"),searchTerm, searchTerm) : Connector.getStatement(Queries.getQuery("findBanByHostmask"),searchTerm,searchTerm)){
            try(ResultSet rs = stmt != null ? stmt.getResultSet() : null){
                while(rs != null && rs.next()){
                    int banId = rs.getInt("banid");
                    String reason = rs.getString("reason");
                    String duration = rs.getString("duration");
                    String thread = rs.getString("thread");
                    String value = rs.getString("value");
                    String channel = rs.getString("channel");
                    returnInfo.computeIfAbsent(banId, i -> new BanInfo(reason, duration, thread, channel));
                    String type = rs.getString("type");
                    switch (type) {
                        case "username":
                            returnInfo.get(banId).addUsername(value);
                            break;
                        case "hostmask":
                            returnInfo.get(banId).addHostmask(value);
                            break;
                    }

                }
            }
            List<String> responses = new ArrayList<>();
            if(returnInfo.size() > 5) {
                Iterator<Integer> itr = returnInfo.keySet().iterator();
                for(int i = 0; i < 5; i++){
                    Integer banid = itr.next();
                    responses.add(Colors.BOLD + banid + Colors.NORMAL + " :" + returnInfo.get(banid).toString());
                }
                responses.add(" there were: " + (returnInfo.size() - 5) + " additional results.  You might want to be more specific.");
            }else{
                returnInfo.forEach((key, value) -> responses.add(Colors.BOLD + key + Colors.NORMAL + " : " + value.toString()));
            }
            return responses;
        }catch(Exception ex){
            logger.error("Something blew up in the new ban search",ex);
        }
        return Collections.emptyList();

    }

    public static void main(String[] args) {
        CommandData data = CommandData.getTestData(".confirmDelete");
        System.out.println(enactDeleteBan(data.getSender()));
    }

    public static String beginDeleteBan(CommandData data){
        int id;
        try {
             id = Integer.parseInt(data.getMessageWithoutCommand().trim());
        }catch(NumberFormatException e){
            return "I'm pretty sure that \"" + data.getMessageWithoutCommand().trim() + "\" is not a number.";
        }
        try(CloseableStatement stmt = Connector.getStatement(Queries.getQuery("findPossibleBans"),id)){
            try(ResultSet rs = stmt != null ? stmt.getResultSet() : null) {
                if (rs != null && rs.next()) {
                    int count = rs.getInt("count");
                    if (count == 0) {
                        return "I didn't find any bans for BanId: " + id;
                    }else{
                        deletionConfirmations.put(data.getSender(),id);
                        return "You are going to attempt to delete ban " + id + ". Please respond with .confirmDelete to enact this command, or .cancelDelete to cancel this deletion.";
                    }

                }else{
                    logger.error("Something went very wrong trying to delete banid " + id);
                    return "I'm sorry something has gone quite wrong.  Magnus, a word?";
                }
            }
        }catch(Exception e){
            logger.error(e);
            return "Something has gone quite wrong.  Magnus, a word?";
        }
    }

    public static String updateBan(CommandData data){
        try {
            BanPrep prep = new BanPrep(data);
            if(prep.getFlagSet().contains("i")){
                StringBuilder str = new StringBuilder();
                str.append("Added the following values for banid: " + prep.getBanid());
                for (String user : prep.getUsers()) {
                    CloseableStatement usernamestmt = Connector.getStatement(Queries.getQuery("addUsernameBan"), prep.getBanid(), user);
                    usernamestmt.executeUpdate();
                }
                if(!prep.getUsers().isEmpty()) {
                    str.append(" Usernames: ").append(Colors.BOLD).append(String.join(",", prep.getUsers())).append(Colors.NORMAL);
                }
                for (String hostmask : prep.getHostmasks()) {
                    CloseableStatement hostmaskstmt = Connector.getStatement(Queries.getQuery("addHostmaskBan"), prep.getBanid(), hostmask);
                    hostmaskstmt.executeUpdate();
                }
                if(!prep.getHostmasks().isEmpty()) {
                    str.append(" Hostmasks: ").append(Colors.BOLD).append(String.join(",", prep.getHostmasks()));
                }
                if(prep.getFlagSet().contains("o")){
                    str.append(" Thread: ").append(Colors.BOLD).append(prep.getThread()).append(Colors.NORMAL);
                    CloseableStatement threadStmt = Connector.getStatement(Queries.getQuery("updateBanThread"), prep.getThread(), prep.getBanid());
                    threadStmt.executeUpdate();
                }
                if(prep.getFlagSet().contains("r")){
                    str.append(" Reason: ").append(Colors.BOLD).append(prep.getReason()).append(Colors.NORMAL);
                    CloseableStatement reasonStatement = Connector.getStatement(Queries.getQuery("updateBanReason"), prep.getReason(), prep.getBanid());
                    reasonStatement.executeUpdate();
                }
                if(prep.getFlagSet().contains("t") || prep.getFlagSet().contains("d")){
                    str.append(" EndTime: ").append(Colors.BOLD).append(prep.getEndTime().format(timeFormatter)).append(Colors.NORMAL);
                    CloseableStatement durationStatement = Connector.getStatement(Queries.getQuery("updateBanDuration"),  prep.getEndTime().format(timeFormatter), prep.getBanid());
                    durationStatement.executeUpdate();
                }
                updateBans();
                return str.toString();
            }else{
                return "You must specify the banid with -i to update a ban.";
            }
        }catch(Exception e){
            logger.error("Something went wrong updating a ban.",e);
            return Command.ERROR;
        }
    }

    public static CommandResponse prepareBan(CommandData data) {

        try {
            BanPrep prep = new BanPrep(data);
            if(prep.getEndTime() == null){
                return new CommandResponse(false,"You forgot to specify a time.");
            }
            if(prep.getUsers().isEmpty() && prep.getHostmasks().isEmpty()){
                return new CommandResponse(false,"You didn't specify usernames or hostmasks.");
            }
            if(StringUtils.isEmpty(prep.getChannel())){
                return new CommandResponse(false,"You didn't specify a channel.");
            }
            if(StringUtils.isEmpty(prep.getReason())){
                return new CommandResponse(false,"You didn't specify a reason for this ban.");
            }
            if (!prep.getResponse().isEmpty()) {
                additionConfirmations.put(data.getSender(), prep);
                return new CommandResponse(true,prep.getResponse());
            } else {
                return new CommandResponse(false, Command.ERROR);
            }
        } catch (Exception e) {
            logger.error("Something went pretty wrong: ", e);
            return new CommandResponse(false,e.getMessage());
        }
    }

    public static String cancelBan(String username) {
        additionConfirmations.remove(username);
        return "I have successfully removed your pending ban entry, " + username;
    }

    public static String cancelDeleteBan(String username) {
        deletionConfirmations.remove(username);
        return "I have successfully removed your pending ban deletion, " + username;
    }
}
