package com.helen.bots;

import com.helen.*;
import com.helen.commands.*;
import com.helen.database.*;
import org.apache.log4j.Logger;
import org.jibble.pircbot.IrcException;
import org.jibble.pircbot.NickAlreadyInUseException;
import org.jibble.pircbot.PircBot;
import org.jibble.pircbot.ReplyConstants;
import org.jibble.pircbot.User;

import javax.annotation.Nullable;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class HelenBot extends PircBot implements Bot {
  private static final Logger logger = Logger.getLogger(HelenBot.class);

  private final Command cmd;
  private final Map<String, Boolean> jarvisPresent;

  public HelenBot() throws IrcException, IOException {
    logger
        .info("Initializing HelenBot v" + Configs.getSingleProperty("version").value);
    this.jarvisPresent = new HashMap<>();
    this.cmd = new Command(this);
    setVerbose(true);
    connect();
    joinChannels();
    //Bans.updateBans();
  }

  private void connect() throws IOException, IrcException {
    setLogin(Configs.getSingleProperty("hostname").value);
    setName(Configs.getSingleProperty("bot_name").value);
    try {
      connect(Configs.getSingleProperty("server").value);
    } catch (NickAlreadyInUseException e) {
      identify(Configs.getSingleProperty("pass").value);
    }
    try {
      Thread.sleep(1_000L);
    } catch (InterruptedException ignored) {}
    identify(Configs.getSingleProperty("pass").value);
    try {
      Thread.sleep(2_000L);
    } catch (InterruptedException ignored) {}
  }


  private void checkTells(CommandData data) throws SQLException {
    Collection<Tell> tells = Tells.getTells(data.sender);
    int size = tells.size();
    if (size > 0) {
      sendNotice(data.sender, "You have " + size + " pending tell" + (size > 1 ? "s." : "."));
    }
    for (Tell tell : tells) {
      Tells.clearTells(tell.nickGroupId == null ? tell.target : tell.nickGroupId.toString());
      sendMessage(tell.target, tell.toString());
    }
  }

  private void dispatchTable(@Nullable String channel, String sender, String login,
                             String hostname, String msg) {
    CommandData data = new CommandData(channel, sender, login, hostname, msg);
    try {
      checkTells(data);
    } catch (SQLException e) {
      logger.error("Exception while checking for tells", e);
    }
    cmd.dispatchTable(data);
  }

  private void joinChannels() {
    for (Config channel : Configs.getProperty("autojoin")) {
      String channelName = channel.value.startsWith("#") ? channel.value : '#' + channel.value;
      joinChannel(channelName);
      sendWho(channelName);
    }
  }

  @Override
  public void joinJarvyChannel(String channel) {
    joinChannel(channel);
    sendWho(channel);
  }

  @Override
  public void jarvisReset(String channel) {
    jarvisPresent.remove(channel.toLowerCase());
  }

  @Override
  public boolean jarvisIsPresent(@Nullable String channel) {
    return channel != null && jarvisPresent.getOrDefault(channel.toLowerCase(), false);
  }

  @Override
  public void sendWho(String channel) {
    sendRawLine("WHO " + channel);
  }

  @Override
  public void log(String line) {
    if (!line.contains("PING :") && !line.contains(">>>PONG")) {
      logger.info(System.currentTimeMillis() + " " + line);
    }
  }

  @Override
  public void onMessage(String channel, String sender, String login, String hostname, String msg) {
    try {
      Users.insertUser(sender, hostname, msg, channel.toLowerCase());
      dispatchTable(channel, sender, login, hostname, msg);
    } catch (SQLException e) {
      logger.error("Database error during response", e);
    }
  }

  @Override
  public void onPrivateMessage(String sender, String login, String hostname, String msg) {
    dispatchTable(null, sender, login, hostname, msg);
  }

  @Override
  protected void onUserList(String channel, User[] users) {
    logger.info("Received user list for a channel" + channel);
  }

  @Override
  public void onServerResponse(int code, String response) {
    if (code == ReplyConstants.RPL_WHOREPLY) {
      logger.info(response);
      String[] splitResponse = Utils.split(' ', response);
      if (splitResponse.length > 5 && "jarvis".equalsIgnoreCase(splitResponse[5])) {
        jarvisPresent.put(splitResponse[1].toLowerCase(), true);
      }
    }
  }

  @Override
  public void onDisconnect() {
    int tries = 0;
    while (!isConnected()) {
      try{
        tries++;
        connect();
        if (isConnected()) {
          joinChannels();
        }
      } catch(IOException | IrcException e) {
        logger.error("Error while disconnecting", e);
        if (tries > 10) {
          logger.error("Shutting down HelenBot!");
          System.exit(1);
        }
        try {
          Thread.sleep(10_000);
        } catch(InterruptedException ignored) {}
      }
    }
  }

  @Override
  public void onPart(String channel, String sender, String login, String hostname) {
    if ("jarvis".equalsIgnoreCase(sender)) {
      jarvisPresent.remove(channel.toLowerCase());
    }
  }

  @Override
  public void onQuit(String sourceNick, String sourceLogin, String sourceHostname, String reason) {
    if ("jarvis".equalsIgnoreCase(sourceNick)) {
      for (String channel : jarvisPresent.keySet()) {
        jarvisPresent.put(channel, false);
      }
    }
  }

  @Override
  public void onJoin(String channel, String sender, String login, String hostmask) {
    if ("jarvis".equalsIgnoreCase(sender)) {
      jarvisPresent.put(channel.toLowerCase(), true);
    }
    BanInfo info = Bans.getUserBan(sender, hostmask, channel);
    if (info != null) {
      kick(channel, sender, info.banReason);
      ban(channel, hostmask);
    }
  }
}
