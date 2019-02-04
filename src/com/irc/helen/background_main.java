package com.irc.helen;

import com.helen.*;
import com.helen.background.*;
import com.helen.database.*;
import org.apache.log4j.Logger;

public class background_main {
  private static final Logger logger = Logger.getLogger(background_main.class);

	public static void main(String[] args) throws Exception {
	  logger.info("Started background processes.");
    Utils.timed("Scanned attributions", logger::info, AttributionFetcher::update);
    Utils.timed("Scanned bans", logger::info, Bans::updateBans);
    Utils.timed("Scanned pages", logger::info, PageFetcher::update);
    Utils.timed("Scanned staff", logger::info, StaffFetcher::update);
    Utils.timed("Scanned titles", logger::info, TitleFetcher::update);
	}
}
