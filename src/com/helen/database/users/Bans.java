package com.helen.database.users;


import org.apache.log4j.Logger;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.net.URL;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class Bans {

    private static final Logger logger = Logger.getLogger(Bans.class);
    static DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM/dd/yyyy");
    private static ConcurrentHashMap<String, HashSet<BanInfo>> bans = new ConcurrentHashMap<>();
    private static List<String> problematicEntries = new ArrayList<>();

    public static void updateBans() throws IOException {


        bans.clear();

        URL url = new URL("http://05command.wikidot.com/chat-ban-page");
        Document result = Jsoup.parse(url, 3000);
        Element table19 = result.select("table").get(0);
        Element table17 = result.select("table").get(1);

        HashSet<BanInfo> site19Bans = populateBanList(table19);
        bans.put("#site19", site19Bans);
        bans.put("#thecritters", site19Bans);
        bans.put("#workshop", site19Bans);
        bans.put("#site17", populateBanList(table17));
    }

    private static HashSet<BanInfo> populateBanList(Element table) {
        HashSet<BanInfo> banList = new HashSet<>();
        Elements rows = table.select("tr");
        problematicEntries.clear();
        for (int i = 2; i < rows.size(); i++) {
            Element row = rows.get(i);
            //skip first two rows
            Elements entries = row.select("td");
            String names = entries.get(0).text();
            String ips = entries.get(1).text();
            String date = entries.get(2).text();
            String reason = entries.get(3).text();
            try {
                List<String> nameList = Arrays.asList(names.split(" "));
                List<String> ipList = Arrays.asList(ips.split(" "));
                boolean isSpecial = false;
                for (String s : ipList) {
                    if (s.contains("@") || s.contains("*")) {
                        isSpecial = true;
                        break;
                    }
                }
                LocalDate bdate;

                if (date.contains("/")) {
                    bdate = LocalDate.parse(date, formatter);
                } else {
                    bdate = LocalDate.parse("12/31/2999", formatter);
                }
                banList.add(new BanInfo(nameList, ipList, reason, bdate, isSpecial));
            } catch (Exception e) {
                logger.error("There was an issue with this entry: " + names + " " + ips + " " + date + " " + reason);
                problematicEntries.add(names + "|" + date);
            }

        }
        return banList;
    }

    public static boolean getSuperUserBan(String username, String hostmask, String login) {
        return getUserBan(username, hostmask, "#site19", login) != null &&
                getUserBan(username, hostmask, "#site17", login) != null;
    }


    public static List<String> getProblematicEntries() {
        return problematicEntries;
    }

    public static BanInfo getUserBan(String username, String hostmask, String channel, String login) {
        LocalDate today = LocalDate.now();
        if (bans.containsKey(channel)) {
            for (BanInfo info : bans.get(channel)) {
                if ((info.getIPs().contains(hostmask) || info.getUserNames().contains(username)) && info.getBanEnd().isAfter(today)) {
                    return info;
                } else if (info.isSpecial()) {
                    for (String infoHostMask : info.getIPs()) {
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
}