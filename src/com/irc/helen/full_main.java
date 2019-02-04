package com.irc.helen;

import com.helen.*;
import com.helen.background.*;
import com.helen.bots.*;
import com.helen.database.*;
import org.apache.log4j.Logger;

import java.util.Date;

public class full_main {
  private static final Logger logger = Logger.getLogger(full_main.class);

  public static void main(String[] args) {
    try {
      logger.info("Starting up HelenBot process at: " + new Date().toString());
      HelenBot helen = Utils.timed("Initialized HelenBot", logger::info, HelenBot::new);
      logger.info(helen.toString());
      new Thread(() -> run("attributions", AttributionFetcher::update)).start();
      new Thread(() -> run("bans", Bans::updateBans)).start();
      new Thread(() -> run("pages", PageFetcher::update)).start();
      new Thread(() -> run("staff", StaffFetcher::update)).start();
      new Thread(() -> run("titles", TitleFetcher::update)).start();
    } catch (Exception e) {
      logger.error("Main thread error", e);
      e.printStackTrace();
      System.exit(1);
    }
  }

  private static void run(String label, Runnable f) {
    logger.info("Starting " + label + " fetcher." );
    while (true) {
      Utils.timed("Finished " + label + " fetcher", logger::info, f);
      try {
        Thread.sleep(Configs.getTimer(label));
      } catch (InterruptedException e) {
        logger.error("Interrupted " + label + " fetcher! Terminating process.", e);
        return;
      }
    }
  }
}
