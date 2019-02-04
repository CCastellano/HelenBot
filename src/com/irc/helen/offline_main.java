package com.irc.helen;

import com.helen.bots.*;

import java.util.Scanner;

public class offline_main {

  public static void main(String[] args) {
    OfflineBot bot = new OfflineBot();
    System.out.println("Awaiting input.");
    try (Scanner scan = new Scanner(System.in)) {
      String line;
      while((line = scan.nextLine()) != null) {
        bot.onMessage("[channel]", "[user]", "[login]", "[hostmask]", line);
      }
    }
  }
}
