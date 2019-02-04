package com.helen.database;


import org.apache.log4j.Logger;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import javax.annotation.Nullable;
import java.io.IOException;
import java.net.URL;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashSet;

public final class Bans {
  private static final Logger logger = Logger.getLogger(Bans.class);

	private static final String PAGE = "http://05command.wikidot.com/chat-ban-page";
	private static final int TIMEOUT = 10_000;

	private static final Collection<BanInfo> bansIn19 = new HashSet<>();
	private static final Collection<BanInfo> bansIn17 = new HashSet<>();

	public static boolean updateBans() {
	  try {
      bansIn19.clear();

      Elements result = Jsoup.parse(new URL(PAGE), TIMEOUT).getElementsByTag("table");
      if (result.size() <= 1) {
        logger.warn("Unable to parse ban page");
        return false;
      }
      Element table19 = result.select("table").get(0);
      Element table17 = result.select("table").get(1);

      populateBanList(bansIn19, table19);
      populateBanList(bansIn17, table17);
      return true;
    } catch (IOException e) {
	    logger.warn("Error loading ban page", e);
	    return false;
    }
	}

	private static void populateBanList(Collection<BanInfo> banList, Element table) {
		Elements rows = table.getElementsByTag("tr");
		for (int i = 2; i < rows.size(); i++) {
			Element row = rows.get(i);
			Elements entries = row.getElementsByTag("td");
			if (entries.size() > 3) {
			  banList.add(new BanInfo(entries));
      }
		}
	}

  public static Collection<BanInfo> getBans() {
	  Collection<BanInfo> bans = new HashSet<>(bansIn19);
	  bans.addAll(bansIn17);
	  return bans;
  }

	@Nullable
	public static BanInfo getUserBan(String username, String hostmask, String channel) {
		LocalDate today = LocalDate.now();
		final Collection<BanInfo> bans;
		switch (channel.toLowerCase()) {
			case "#site17":
				bans = bansIn17;
				break;

			case "#site19":
			case "#thecritters":
				bans = bansIn19;
				break;

			default:
				return null;
		}
		return bans
        .stream()
        .filter(info -> info.banEnd.isAfter(today) &&
                        (info.IPs.contains(hostmask) || info.userNames.contains(username)))
        .findAny().orElse(null);
	}
}
