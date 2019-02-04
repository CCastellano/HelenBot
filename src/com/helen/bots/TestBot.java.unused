package com.helen.bots;

import org.jibble.pircbot.PircBot;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.net.URL;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;

public class TestBot extends PircBot {


	public static void main(String[] args) throws Exception {
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM/dd/yyyy");

		URL url = new URL("http://05command.wikidot.com/chat-ban-page");
		Document result = Jsoup.parse(url, 3000);
		Element table = result.select("table").get(0);
		Elements rows = table.select("tr");
		for (int i = 2; i < rows.size(); i++) {
			Element row = rows.get(i);
			//skip first two rows
			Elements entries = row.select("td");
			String names = entries.get(0).text();
			String ips = entries.get(1).text();
			String date = entries.get(2).text();
			String reason = entries.get(3).text();

			List<String> nameList = Arrays.asList(names.split(" "));
			List<String> ipList = Arrays.asList(ips.split(" "));
			LocalDate bdate;


			if (date.contains("/")) {
				bdate = LocalDate.parse(date, formatter);
			} else {
				bdate = LocalDate.parse("12/31/2999", formatter);
			}
		}
	}

}
