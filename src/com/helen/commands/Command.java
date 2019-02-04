package com.helen.commands;

import com.helen.*;
import com.helen.bots.*;
import com.helen.database.*;
import com.helen.database.selectable.*;
import com.helen.error.*;
import com.helen.search.*;
import org.apache.commons.cli.ParseException;
import org.apache.log4j.Logger;
import org.apache.xmlrpc.XmlRpcException;
import org.jibble.pircbot.User;

import javax.annotation.Nullable;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@SuppressWarnings("unused")
public class Command {
  private static final Logger logger = Logger.getLogger(Command.class);

  public static final String CHANNEL_ONLY = "I can only do that in a channel.";
  public static final String MORE_ARGS = "That command requires more arguments.";
  public static final String NOT_FOUND = "I'm sorry, I couldn't find anything.";

  private static final int ADMIN_SECURITY = 2;

  private static final Map<String, Method> hashableCommandList = new HashMap<>();
  private static final Map<String, Method> slowCommands = new HashMap<>();
  private static final Map<String, Method> regexCommands = new HashMap<>();
  static {
    logger.info("Initializing commandList.");
    for (Method m : Command.class.getDeclaredMethods()) {
      if (m.isAnnotationPresent(IRCCommand.class)) {
        IRCCommand cmd = m.getAnnotation(IRCCommand.class);
        if (cmd.startOfLine() && !cmd.reg()) {
          for (String s : cmd.command()) {
            hashableCommandList.put(s.toLowerCase(), m);
          }
        } else if (!cmd.reg()) {
          for (String s : cmd.command()) {
            slowCommands.put(s.toLowerCase(), m);
          }
        } else {
          for (String s : cmd.regex()) {
            regexCommands.put(s, m);
          }
        }
        for (String s: cmd.command()) {
          logger.info("Loaded command: " + m + " with activation string " + s);
        }
      }
    }
    logger.info("Finished initializing commandList.");
  }

  public static String error() {
    return "I'm sorry, there was an error. Please inform " +
           Configs.getSingleProperty("contact").value + '.';
  }

  private static <T> T notNull(@Nullable T t) throws IncorrectUsageException {
    if (t == null) {
      throw new IncorrectUsageException(MORE_ARGS);
    }
    return t;
  }

  private static String buildResponse(Stream<?> dbo) {
    return '{' + dbo.map(Object::toString).collect(Collectors.joining(", ")) + '}';
  }

  private final Bot helen;

  private boolean adminMode = false;
  private int bullets = 6;

  public Command(Bot helen) {
    this.helen = helen;
  }

  private int getSecurityLevel(CommandData data) {
    User[] userlist = data.channel == null ? null : helen.getUsers(data.channel);
    if (data.isWhiteList()) {
      return 4;
    } else if (userlist != null) {
      for (User user : userlist) {
        if (data.sender.equalsIgnoreCase(user.getNick())) {
          if (user.isOp()) {
            return 3;
          }
          switch (user.getPrefix()) {
            case "~":
            case "&":
              return 3;

            case "%":
              return 2;

            default:
              return 1;
          }
        }
      }
    }
    return 1;
  }

  private boolean jarvisPermits(CommandData data, Method m) {
    return m.getAnnotation(IRCCommand.class).coexistWithJarvis()
           || data.channel == null
           || !helen.jarvisIsPresent(data.channel.toLowerCase());
  }

  private int securityThreshold(Method m) {
    return adminMode
           ? StrictMath.max(m.getAnnotation(IRCCommand.class).securityLevel(), ADMIN_SECURITY)
           : m.getAnnotation(IRCCommand.class).securityLevel();
  }
  private String unauthorized(CommandData data, int securityLevel) {
    return "User " + data.sender + " attempted to use command: " +
           data.getCommand() + " which is above their security level of: " +
           securityLevel + (adminMode ? ". I am currently in admin mode." : ".");
  }

  private void onError(CommandData data, String label, Exception e) {
    if (e instanceof InvocationTargetException) {
      Throwable error = ((InvocationTargetException) e).getTargetException();
      if (error instanceof IncorrectUsageException) {
        helen.sendReply(data, error.getMessage());
      } else {
        helen.sendReply(data, error());
        logger.error("Exception invoking " + label + ": " + data.getCommand(), error);
      }
    } else {
      logger.error("Exception invoking " + label + ": " + data.getCommand(), e);
    }
  }

  public void dispatchTable(CommandData data) {
    int securityLevel = getSecurityLevel(data);
    String cmd = data.getCommand();
    Method mHash = cmd == null ? null : hashableCommandList.get(cmd.toLowerCase());
    // If we can use hashcommands, do so
    if (mHash != null) {
      try {
        if (jarvisPermits(data, mHash)) {
          if (securityLevel >= securityThreshold(mHash)) {
            mHash.invoke(this, data);
          } else {
            logger.info(unauthorized(data, securityLevel));
          }
        }
      } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
        onError(data, "start-of-line command", e);
      }
      // otherwise, run the command string against all the contains
      // commands
    } else {
      for (Map.Entry<String, Method> stringMethodEntry : slowCommands.entrySet()) {
        if (data.message.toLowerCase().contains(stringMethodEntry.getKey().toLowerCase())) {
          try {
            Method mContains = stringMethodEntry.getValue();
            if (jarvisPermits(data, mContains)) {
              if (securityLevel >= securityThreshold (mContains)) {
                mContains.invoke(this, data);
              } else {
                logger.info(unauthorized(data, securityLevel));
              }
            }
          } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
            onError(data, "command", e);
          }
        }
      }

      // lastly check the string against any regex commands
      if (cmd != null) {
        for (Map.Entry<String, Method> stringMethodEntry : regexCommands.entrySet()) {
          Pattern r = Pattern.compile(stringMethodEntry.getKey(), Pattern.CASE_INSENSITIVE);

          Matcher match = r.matcher(cmd);
          if (match.matches()) {
            Method mRegex = stringMethodEntry.getValue();
            IRCCommand irc = mRegex.getAnnotation(IRCCommand.class);
            try {
              if (irc.matcherGroup() != -1) {
                data.regexTarget = match.group(irc.matcherGroup());
              }
              if (jarvisPermits(data, mRegex)) {
                if (securityLevel >= securityThreshold(mRegex)) {
                  mRegex.invoke(this, data);
                } else {
                  logger.info(unauthorized(data, securityLevel));
                }
              }
            } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
              onError(data, "RegEx command", e);
            }
          }
        }
      }
    }
  }

  // Relatively unregulated commands (anyone can try these)
  @IRCCommand(command = { ".HelenBot" }, startOfLine = true, coexistWithJarvis = true, securityLevel = 1)
  public void versionResponse(CommandData data) {
    Config version = Configs.getSingleProperty("version");
    helen.sendReply(data, "Greetings! I am HelenBot v" + version.value + '.');
  }

  @IRCCommand(command = { ".modeToggle" }, startOfLine = true, coexistWithJarvis = true, securityLevel = 3)
  public void toggleMode(CommandData data) {
    adminMode = !adminMode;
    helen.sendReply(data, "I am now in " + (adminMode ? "Admin Only" : "Any User") + " mode.");
  }

  @IRCCommand(command = { ".checkJarvis" }, startOfLine = true, coexistWithJarvis = true, securityLevel = 2)
  public void findJarvisInChannel(CommandData data) {
    if (data.channel == null) {
      helen.sendReply(data, Command.CHANNEL_ONLY);
    } else {
      helen.jarvisReset(data.channel);
      helen.sendWho(data.channel);
      helen.sendReply(data, "Checking channel members…");
    }
  }

  @IRCCommand(command = { ".jarvistest" }, startOfLine = true, coexistWithJarvis = true, securityLevel = 4)
  public void listTest(CommandData data) {
    helen.sendReply(data, Boolean.toString(helen.jarvisIsPresent(data.getTarget())));
  }

  @IRCCommand(command = { ".ch", ".choose" }, startOfLine = true, securityLevel = 1)
  public void choose(CommandData data) throws IncorrectUsageException {
    String message = data.getMessageWithoutCommand();
    if (message == null) {
      helen.sendReply(data, "I choose nothing.");
    } else {
      String[] choices = Utils.split(',', notNull(data.getMessageWithoutCommand()));
      helen.sendReply(data, choices[new Random().nextInt(choices.length)]);
    }
  }

  @IRCCommand(command = { ".mode" }, startOfLine = true, coexistWithJarvis = true, securityLevel = 2)
  public void displayMode(CommandData data) {
    helen.sendReply(data, "I am currently in " + (adminMode ? "Admin Only" : "Any User") + " mode.");
  }

  @IRCCommand(command = { ".msg" }, startOfLine = true, securityLevel = 1)
  public void sendMessage(CommandData data) throws IncorrectUsageException {
    helen.sendMessage(notNull(
        data.getTarget()),
        data.sender + " said: " + notNull(data.getPayload())
    );
  }

  @IRCCommand(command = {".addNick"}, startOfLine = true, coexistWithJarvis = true, securityLevel = 1)
  public void addNick(CommandData data) throws SQLException {
    helen.sendReply(data, Nicks.addNick(data));
  }

  @IRCCommand(command = {".deleteNick"}, startOfLine = true, coexistWithJarvis = true, securityLevel = 1)
  public void deleteNick(CommandData data) throws SQLException {
    helen.sendReply(data, Nicks.deleteNick(data));
  }

  @IRCCommand(command = {".deleteAllNicks"}, startOfLine = true, coexistWithJarvis = true, securityLevel = 1)
  public void deleteAllNicks(CommandData data) throws SQLException {
    helen.sendReply(data, Nicks.deleteAllNicks(data, true));
  }

  @IRCCommand(command = {".deleteNicksAdmin"}, startOfLine = true, coexistWithJarvis = true, securityLevel = 4)
  public void deleteNicksAdmin(CommandData data) throws SQLException {
    helen.sendReply(data, Nicks.deleteAllNicks(data, false));
  }

  @IRCCommand(command = { ".roll" }, startOfLine = true, securityLevel = 1)
  public void roll(CommandData data) throws IncorrectUsageException, SQLException {
    String result = Rolls.roll(notNull(data.getMessageWithoutCommand()), data.sender);
    helen.sendReply(data, result);
  }

  @IRCCommand(command = { ".myRolls", ".myrolls" }, startOfLine = true, securityLevel = 1)
  public void getRolls(CommandData data) throws SQLException {
    Collection<Roll> rolls = Rolls.getRolls(data.sender);
    if (rolls.isEmpty()) {
      helen.sendReply(data, "*Checks her clipboard* Apologies, I do not have any saved rolls for you at this time.");
    } else {
      helen.sendMessage(data.getResponseTarget(), buildResponse(rolls.stream()));
    }
  }

  @IRCCommand(command = {".hugme"}, startOfLine = true, coexistWithJarvis = true, securityLevel = 1)
  public void hugMe(CommandData data) throws IncorrectUsageException {
    if (data.isHugList()) {
      helen.sendReply(data, Hugs.storeHugmessage(data));
    } else {
      helen.sendReply(data, "You're not authorized to do that.");
    }
  }

  @IRCCommand(command={".hugHelen",".helenhug",".hugsplox"}, startOfLine = true, coexistWithJarvis = true, securityLevel = 1)
  public void hug(CommandData data) {
    if (data.isHugList()) {
      helen.sendReply(data, Hugs.getHugMessage(data.sender.toLowerCase()));
    } else if (data.isWhiteList()) {
      helen.sendAction(data.getResponseTarget(), "hugs " + data.sender + '.');
    } else {
      String[] messages = new String[]{
          "Thank you for the display of affection.",
          "*Click* Please remove your hands from my cylinders.",
          "Hugging a revolver must be difficult.",
          "You're smudging my finish.",
          "*Sigh* And I just calibrated my sights…",
          "I'm not sure why you're hugging me but… thank… you?",
          "Yes… Human emotion. This is… nice. Please let go of me."};

      helen.sendReply(data, messages[new Random().nextInt(messages.length)]);
    }
  }

  @IRCCommand(command = { ".average", ".avg" }, startOfLine = true, securityLevel = 1)
  public void getAverage(CommandData data) throws IncorrectUsageException, SQLException {
    helen.sendReply(data, Rolls.getAverage(notNull(data.getTarget()), data.sender));
  }

  @IRCCommand(command = { ".g", ".google" }, startOfLine = true, securityLevel = 1)
  public void webSearch(CommandData data) throws IncorrectUsageException {
    GoogleResults results = WebSearch.search(notNull(data.getMessageWithoutCommand()));
    helen.sendReply(data, results == null ? null : results.toString());
  }

  @IRCCommand(command = { ".gis" }, startOfLine = true, securityLevel = 1)
  public void imageSearch(CommandData data) throws IncorrectUsageException {
    GoogleResults results = WebSearch.imageSearch(notNull(data.getMessageWithoutCommand()));
    helen.sendReply(data, results == null ? null : results.toString());
  }

  @IRCCommand(command = { ".w", ".wiki", ".wikipedia" }, startOfLine = true, securityLevel = 1)
  public void wikipediaSearch(CommandData data) throws IncorrectUsageException {
    helen.sendReply(data, WikipediaSearch.search(data, notNull(data.getMessageWithoutCommand())));
  }

  @IRCCommand(command = { ".y", ".yt", ".youtube" }, startOfLine = true, securityLevel = 1)
  public void youtubeSearch(CommandData data) throws IncorrectUsageException {
    helen.sendReply(data, YouTubeSearch.youtubeSearch(notNull(data.getMessageWithoutCommand())));
  }

  @IRCCommand(command = {".helen", ".helenHelp"}, startOfLine = true, securityLevel = 1, coexistWithJarvis = true)
  public void helenHelp(CommandData data) {
    help(data);
  }

  @IRCCommand(command = ".help", startOfLine = true, securityLevel = 1)
  public void help(CommandData data) {
    helen.sendReply(data, "You can find a list of my job responsibilities here: http://home.helenbot.com/usage.html");
  }

  @IRCCommand(command = ".seen", startOfLine = true, securityLevel = 1)
  public void seen(CommandData data) throws IncorrectUsageException, SQLException {
    helen.sendMessage(data.getResponseTarget(), Users.seen(data));
  }

  @IRCCommand(command = ".sm", startOfLine = true, securityLevel = 1)
  public void selectResult(CommandData data) throws IncorrectUsageException {
    helen.sendReply(data, StoredCommands.run(notNull(data.getTarget()), data.sender));
  }

  @IRCCommand(command = {".lc",".l"}, startOfLine = true, securityLevel = 1)
  public void lastCreated(CommandData data)
      throws IOException, XmlRpcException, IncorrectUsageException, SQLException {
    for (String page : Pages.lastCreated(data)) {
      helen.sendReply(data, page);
    }
  }

  @IRCCommand(command = {".hlc", ".hl"}, coexistWithJarvis = true, startOfLine = true, securityLevel = 1)
  public void lastCreatedHelen(CommandData data)
      throws IncorrectUsageException, XmlRpcException, IOException, SQLException {
    lastCreated(data);
  }

  @IRCCommand(command = ".au", startOfLine = true, securityLevel = 1)
  public void authorDetail(CommandData data) throws SQLException {
    String msg = data.getMessageWithoutCommand();
    helen.sendReply(data, Pages.getAuthorDetail(data, msg == null ? data.sender : msg));
  }

  @IRCCommand(command = "SCPPAGEREGEX", startOfLine= true, reg = true, regex = { "http:\\/\\/www.scp-wiki.net\\/(.*)" }, securityLevel = 1, matcherGroup = 1)
  public void getPageInfo(CommandData data) throws XmlRpcException, SQLException {
    String regexTarget = data.regexTarget;
    if (regexTarget != null && !regexTarget.contains("/") && !regexTarget.contains("forum")) {
      helen.sendReply(data, Pages.getPageInfo(regexTarget));
    }
  }

  @IRCCommand(command = "SCP", startOfLine = true, reg = true, regex = { "(scp|SCP)-([^\\s]+)(-(ex|EX|j|J|arc|ARC))?" }, securityLevel = 1)
  public void scpSearch(CommandData data)
      throws IncorrectUsageException, XmlRpcException, SQLException {
    helen.sendReply(data, Pages.getPageInfo(notNull(data.getCommand())));
  }

  @IRCCommand(command = {".s",".sea",".search"}, startOfLine = true, securityLevel = 1)
  public void findSkip(CommandData data) throws SQLException, XmlRpcException, ParseException {
    helen.sendReply(data, Pages.getPotentialTargets(data.splitMessage, data.sender));
  }

  private static final Pattern SCP = Pattern.compile("(scp|SCP)-([^\\s]+)(-(ex|EX|j|J|arc|ARC))?");
  @IRCCommand(command = {".hs", ".hsea"}, coexistWithJarvis = true, startOfLine = true, securityLevel = 1)
  public void findSkipHelen(CommandData data)
      throws IncorrectUsageException, XmlRpcException, SQLException, ParseException {
    if (SCP.matcher(notNull(data.getTarget())).matches()) {
      helen.sendReply(data, Pages.getPageInfo(data.getTarget()));
    } else {
      helen.sendReply(data, Pages.getPotentialTargets(data.splitMessage, data.sender));
    }
  }

  @IRCCommand(command = {".pronouns", ".pronoun"}, startOfLine = true, coexistWithJarvis = true, securityLevel = 1)
  public void getPronouns(CommandData data) throws SQLException {
    String target = data.getTarget();
    helen.sendReply(data, Pronouns.getPronouns(target == null ? data.sender : target));
  }

  @IRCCommand(command = ".myPronouns", startOfLine = true, coexistWithJarvis = true, securityLevel = 1)
  public void myPronouns(CommandData data) throws SQLException {
    helen.sendReply(data, Pronouns.getPronouns(data.sender));
  }
  //TODO make this less stupid
  @IRCCommand(command = ".helenconf", startOfLine = true, coexistWithJarvis = true, securityLevel = 4)
  public void configure(CommandData data) throws IncorrectUsageException {
    if (data.splitMessage.length == 1) {
      helen.sendReply(data, "{shoot|lcratings}");
    } else if (data.splitMessage.length <= 2) {
      throw new IncorrectUsageException(MORE_ARGS);
    } else {
      helen.sendReply(data, Configs.insertToggle(data, notNull(data.getTarget()),
          "true".equalsIgnoreCase(data.splitMessage[2])));
    }
  }

  @IRCCommand(command = ".setPronouns", startOfLine = true, coexistWithJarvis = true, securityLevel = 1)
  public void setPronouns(CommandData data) throws IncorrectUsageException, SQLException {
    String response = Pronouns.insertPronouns(data);
    if (response.contains("banned term")) {
      for (Config c : Configs.getProperty("registeredNicks")) {
        Tells.sendTell(c.value, Configs.getSingleProperty("BOT_NAME").value,
            "User " + data.sender + " attempted to add a banned term:" + response +
            ". Their full message was: " + data.message, true);

      }
    }
    helen.sendReply(data, response);
  }

  @IRCCommand(command = ".clearPronouns", startOfLine = true, coexistWithJarvis = true, securityLevel = 1)
  public void clearPronouns(CommandData data) throws SQLException {
    helen.sendReply(data, Pronouns.clearPronouns(data.sender));
  }

  @IRCCommand(command = ".deletePronouns", startOfLine = true, coexistWithJarvis = true, securityLevel = 2)
  public void removePronouns(CommandData data) throws IncorrectUsageException, SQLException {
    helen.sendReply(data, Pronouns.clearPronouns(notNull(data.getTarget())));
  }

  @IRCCommand(command = {".def",".definition"}, startOfLine = true, securityLevel = 1)
  public void define(CommandData data)
      throws IncorrectUsageException, ParserConfigurationException {
    helen.sendReply(data, WebsterSearch.dictionarySearch(notNull(data.getMessageWithoutCommand())));
  }

  // Authentication Required Command
  @IRCCommand(command = ".join", startOfLine = true, coexistWithJarvis = true, securityLevel = 3)
  public void enterChannel(CommandData data) throws IncorrectUsageException {
    helen.joinJarvyChannel(notNull(data.getTarget()));

  }

  @IRCCommand(command = ".leave", startOfLine = true, coexistWithJarvis = true, securityLevel = 3)
  public void leaveChannel(CommandData data) throws IncorrectUsageException {
    helen.partChannel(notNull(data.getTarget()));
  }

  @IRCCommand(command = ".tell", startOfLine = true, securityLevel = 1)
  public void tell(CommandData data) throws IncorrectUsageException, SQLException {
    String str = Tells.sendTell(
        notNull(data.getTarget()), data.sender, notNull(data.getTellMessage()), data.channel == null
    );
    helen.sendReply(data, str);
  }

  @IRCCommand(command = ".mtell", startOfLine = true, securityLevel = 1,coexistWithJarvis = true)
  public void multiTell(CommandData data) throws IncorrectUsageException, SQLException {
    String str = Tells.sendMultitell(data);
    helen.sendReply(data, str);
  }

  @IRCCommand(command = ".exit", startOfLine = true, coexistWithJarvis = true, securityLevel = 4)
  public void exitBot(CommandData data) {
    for (String channel : helen.getChannels()) {
      helen.partChannel(channel, "Stay out of the revolver's sights…");
    }
    try {
      Thread.sleep(5_000);
    } catch (InterruptedException ignored) {}
    helen.disconnect();
    System.exit(0);
  }

  @IRCCommand(command = ".allProperties", startOfLine = true, securityLevel = 3)
  public void getAllProperties(CommandData data) {
    Stream<Config> properties = Configs.getConfiguredProperties(true);
    helen.sendReply(data, "Configured properties: " + buildResponse(properties));
  }

  @IRCCommand(command = ".revealProperties", startOfLine = true, securityLevel = 4)
  public void revealProperties(CommandData data) {
    Stream<Config> properties = Configs.getConfiguredProperties(false);
    helen.sendReply(data, "Configured properties: " + buildResponse(properties));
  }

  @IRCCommand(command = ".property", startOfLine = true, coexistWithJarvis = true, securityLevel = 2)
  public void getProperty(CommandData data) throws IncorrectUsageException {
    Stream<Config> properties = Configs
        .getProperty(notNull(data.getTarget()))
        .stream()
        .filter(x -> x.isPublic);
    helen.sendReply(data, "Configured properties: " + buildResponse(properties));
  }

  @IRCCommand(command = ".setProperty", startOfLine = true, coexistWithJarvis = true, securityLevel = 4)
  public void setProperty(CommandData data) throws IncorrectUsageException {
    if (data.splitMessage.length <= 3) {
      throw new IncorrectUsageException(MORE_ARGS);
    }
    String properties = Configs.setProperty(
        data.splitMessage[1],
        data.splitMessage[2],
        "t".equalsIgnoreCase(data.splitMessage[3])
    );
    helen.sendReply(data, properties);
  }

  @IRCCommand(command = ".updateProperty", startOfLine = true, coexistWithJarvis = true, securityLevel = 4)
  public void updateProperty(CommandData data) throws IncorrectUsageException {
    if (data.splitMessage.length <= 3) {
      throw new IncorrectUsageException(MORE_ARGS);
    }
    String properties = Configs.updateSingle(
        data.splitMessage[1],
        data.splitMessage[2],
        "t".equalsIgnoreCase(data.splitMessage[3])
    );
    helen.sendReply(data, properties);
  }

  @IRCCommand(command = ".deleteProperty", startOfLine = true, securityLevel = 4)
  public void deleteProperty(CommandData data) throws IncorrectUsageException {
    if (data.splitMessage.length <= 2) {
      throw new IncorrectUsageException(MORE_ARGS);
    }
    String properties = Configs.removeProperty(data.splitMessage[1], data.splitMessage[2]);
    helen.sendReply(data, properties);
  }

  @IRCCommand(command = {".clearCache",".clear"}, startOfLine = true, securityLevel = 4)
  public static void clearCache(CommandData data) {
    Statements.clear();
    Configs.clear();
    Pronouns.reload();
  }

  @IRCCommand(command = ".shoot", startOfLine = true, securityLevel = 4, coexistWithJarvis = true)
  public void shootUser(CommandData data) throws IncorrectUsageException {
    if (Configs.commandEnabled(data, "shoot")) {
      String target = notNull(data.getTarget());
      if (Configs.getSingleProperty("BOT_NAME").value.equalsIgnoreCase(target)) {
        bullets--;
        helen.sendAction(data.getResponseTarget(), "shoots " + data.sender + '.');
        if (bullets < 1) {
          reload(data);
        }
      } else {
        helen.sendAction(data.getResponseTarget(), "shoots " + target + '.');
        bullets--;
        if (bullets < 1) {
          reload(data);
        }
        helen.sendMessage(data.getResponseTarget(),
            "Be careful, " + target + ". I still have " +
            (bullets > 1 ? bullets + " bullets left." : "one in the chamber.")
        );
      }
    }
  }

  @IRCCommand(command = ".reload", startOfLine = true, securityLevel = 4)
  public void reload(CommandData data) {
    helen.sendAction(data.getResponseTarget(), "reloads all six cylinders.");
    bullets = 6;
  }

  @IRCCommand(command = ".unload", startOfLine = true, securityLevel = 4, coexistWithJarvis = true)
  public void unload(CommandData data) {
    if (Configs.commandEnabled(data, "shoot")) {
      if (Configs.getSingleProperty("BOT_NAME").value.equalsIgnoreCase(data.getTarget())) {
        bullets--;
        helen.sendAction(data.getResponseTarget(), "shoots " + data.sender);
        if (bullets < 1) {
          reload(data);
        }
      } else {
        helen.sendAction(data.getResponseTarget(),
            "calmly thumbs back the hammer and unleashes" +
            (bullets == 6 ? " all six" : " the remaining " + bullets) +
            " cylinders on " + data.getTarget() + '.'
        );
        helen.sendMessage(data.getResponseTarget(), "Stay out of the revolver's sights.");
        reload(data);
      }
    }
  }

  @IRCCommand(command = ".discord", startOfLine = true, securityLevel = 4, coexistWithJarvis = true)
  public void showDiscordMessage(CommandData data) {
    helen.sendMessage(data.getResponseTarget(),
        "There are currently no plans for an official SCP Discord. " +
        "Staff feel that, at this time, the benefits of Discord do not outweigh the difficulties " +
        "of moderation, and the resulting fracturing between IRC and Discord. " +
        "There are also several concerns about the technical and financial viability of Discord.");
  }

  @IRCCommand(command = ".updateBans", startOfLine = true, securityLevel = 4, coexistWithJarvis = true)
  public void updateBans(CommandData data) {
    helen.sendMessage(data.getResponseTarget(),
        Bans.updateBans()
        ? "Ban list successfully updated."
        : "Error parsing chat ban page. Please check the page for correct syntax."
    );
  }

  @IRCCommand(command = ".user", startOfLine = true, securityLevel = 1)
  public void getUserName(CommandData data) throws IncorrectUsageException {
    if (data.splitMessage.length <= 1) {
      throw new IncorrectUsageException(MORE_ARGS);
    }
    Collection<String> list = Arrays.asList(data.splitMessage).subList(1, data.splitMessage.length);
    helen.sendMessage(data.getResponseTarget(),
        data.sender + ": http://www.wikidot.com/user:info/" +
        Arrays.stream(data.splitMessage).skip(1).collect(Collectors.joining("_"))
    );
  }
}
