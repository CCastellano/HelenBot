package com.irc.helen;

import com.helen.*;
import com.helen.bots.*;
import org.apache.log4j.Logger;

import java.util.Date;

public class helen_main {
	private static final Logger logger = Logger.getLogger(helen_main.class);

	public static void main(String[] args) {
	  try {
      logger.info("Starting up HelenBot process at: " + new Date().toString());
      HelenBot helen = Utils.timed("Initialized HelenBot", logger::info, HelenBot::new);
      logger.info(helen.toString());
    } catch (Exception e) {
	    logger.error(e);
	    e.printStackTrace();
	    System.exit(1);
    }
	}
}
