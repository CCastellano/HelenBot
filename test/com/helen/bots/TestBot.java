package com.helen.bots;

import com.helen.commands.*;
import org.jibble.pircbot.Colors;
import org.jibble.pircbot.TestUser;
import org.jibble.pircbot.User;
import org.junit.Assert;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;

public class TestBot implements Bot {
  public static final Pattern CLEAN = Pattern.compile('[' + Colors.BOLD + Colors.NORMAL + ']');

  public static void assertRun(String cmd, String...responses) {
    TestBot bot = new TestBot();
    bot.run(cmd);
    bot.assertResponse(responses);
  }

  private final Command cmd;
  private List<String> responses;

  private final String[] channels;
  private final User[] users;

  public TestBot() {
    this.cmd       = new Command(this);
    this.responses = new ArrayList<>();
    this.channels  = new String[]{"<channel>"};
    this.users     = new User[]{new TestUser("~", "<Sender>")};
  }

  private void dispatchTable(@Nullable String channel, String sender, String login,
                            String hostname, String msg) {
    cmd.dispatchTable(new CommandData(channel, sender, login, hostname, msg));
  }

  public void run(String command) {
    onMessage("<channel>", "<Sender>", "<Login>", "<Hostname>", command);
  }

  @Nullable
  public String search(String cmd) {
    run(cmd);
    return found();
  }

  public void onMessage(String channel, String sender, String login, String hostname, String msg) {
    //Users.insertUser(sender, hostname, msg, channel.toLowerCase());
    dispatchTable(channel, sender, login, hostname, msg);
  }

  public List<String> getResponses() {
    List<String> responses = this.responses;
    this.responses = new ArrayList<>();
    return responses;
  }

  public void assertResponse(String...strs) {
    Collection<String> messages = getResponses();
    Assert.assertEquals("Wrong number of responses", strs.length, messages.size());
    Assert.assertEquals("Unexpected responses", Arrays.asList(strs), messages);
  }

  @Nullable
  public String found() {
    List<String> messages = getResponses();
    return messages.isEmpty() || messages.get(0).contains(Command.NOT_FOUND)
           ? null
           : messages.get(0);
  }

  private void addResponse(String message) {
    responses.add(CLEAN.matcher(message).replaceAll(""));
  }

  @Override
  public void sendMessage(String target, String message) {
    if (channels[0].equals(target) || users[0].getNick().equals(target)) {
      addResponse(message);
    } else {
      addResponse('@' + target + ": " + message);
    }
  }
  @Override
  public void sendAction(String target, String action) {
    addResponse("/me " + action);
  }
  @Override
  public void sendNotice(String target, String notice) {
    addResponse(notice);
  }

  @Override
  public String[] getChannels() {
    return channels;
  }

  @Override
  public User[] getUsers(String channel) {
    return users;
  }
}