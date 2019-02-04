package com.helen.bots;

import com.helen.*;
import com.helen.commands.*;
import org.jibble.pircbot.Colors;
import org.jibble.pircbot.User;

import javax.annotation.Nullable;
import java.util.regex.Pattern;

public class OfflineBot implements Bot {
  private static final String[] STRING_0 = new String[0];
  private static final User[] USER_0 = new User[0];

  private static final Pattern BOLD = Pattern.compile(Colors.BOLD);
  private static final Pattern NORM = Pattern.compile(Colors.NORMAL);

  private static class AdminCommandData extends CommandData {
    public AdminCommandData(@Nullable String channel, String sender, String login, String hostname, String msg) {
      super(channel, sender, login, hostname, msg);
    }

    @Override
    public boolean isWhiteList() {
      return true;
    }
  }

  private final Command cmd;
  private long start;

  public OfflineBot() {
    this.cmd = new Command(this);
    this.start = System.nanoTime();
  }

  private void println(String str) {
    long elapsed = (System.nanoTime() - start) / Utils.NANO;
    System.out.println(
        "(" + elapsed / 1000 + '.' + elapsed % 1000 / 100 + "s) " +
        NORM.matcher(BOLD.matcher(str).replaceAll("\033[1m")).replaceAll("\033[0m")
    );
  }

  private void dispatchTable(@Nullable String channel, String sender, String login,
                             String hostname, String msg) {
    cmd.dispatchTable(new AdminCommandData(channel, sender, login, hostname, msg));
  }

  public void onMessage(String channel, String sender, String login, String hostname, String msg) {
    //Users.insertUser(sender, hostname, msg, channel.toLowerCase());
    start = System.nanoTime();
    dispatchTable(channel, sender, login, hostname, msg);
  }

  @Override
  public void sendMessage(String target, String message) {
    println(message);
  }
  @Override
  public void sendAction(String target, String action) {
    println("> /me " + action);
  }
  @Override
  public void sendNotice(String target, String notice) {
    println("@ " + notice);
  }

  @Override
  public String[] getChannels() {
    return STRING_0;
  }

  @Override
  public User[] getUsers(String channel) {
    return USER_0;
  }

}