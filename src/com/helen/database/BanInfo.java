package com.helen.database;

import com.helen.*;
import org.jsoup.select.Elements;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.Collection;

public class BanInfo {
  private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("MM/dd/yyyy");
  private static final LocalDate FOREVER     = LocalDate.parse("12/31/2999", FMT);

	public final Collection<String> userNames;
	public final Collection<String> IPs;
	public final String banReason;
	public final LocalDate banEnd;

	BanInfo(Elements html) {
    LocalDate banEnd;
    try {
      banEnd = LocalDate.parse(html.get(2).text(), FMT);
    } catch (DateTimeParseException e) {
      banEnd = FOREVER;
    }

    this.userNames = Arrays.asList(Utils.split(' ', html.get(0).text()));
    this.IPs       = Arrays.asList(Utils.split(' ', html.get(1).text()));
    this.banEnd    = banEnd;
    this.banReason = html.get(3).text();
  }
}
