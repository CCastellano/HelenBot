package com.helen.commands;

import com.helen.bots.BotFramework;
import com.helen.database.data.*;
import com.helen.database.framework.Config;
import com.helen.database.framework.Configs;
import com.helen.database.framework.Queries;
import com.helen.database.users.*;
import com.helen.search.*;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.jibble.pircbot.User;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;


public class Command {
    public static final String NOT_FOUND = "I'm sorry, I couldn't find anything.";
    public static final String CONFIG_ERROR = "I'm sorry there's a config error that Magnus must investigate.";
    public static final String ERROR = "I'm sorry, there was an error. Please inform DrMagnus.";
    private static final Logger logger = Logger.getLogger(Command.class);
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static HashMap<String, Method> hashableCommandList = new HashMap<>();
    private static HashMap<String, Method> slowCommands = new HashMap<>();
    private static HashMap<String, Method> regexCommands = new HashMap<>();
    private static ConcurrentHashMap<String, ConcurrentHashMap<String, Long>> cooldowns = new ConcurrentHashMap<>();
    private static ConcurrentHashMap<String, String> dotCommands = new ConcurrentHashMap<>();
    private static Method constructedMethod;
    static {
        logger.info("Initializing commandList.");



        try {
            constructedMethod = Command.class.getMethod("dotCommand", CommandData.class);
            Optional<Map<String, String>> commandList = DotCommand.getDotCommands();
            commandList.ifPresent(stringStringMap -> stringStringMap.keySet().forEach(key -> hashableCommandList.put(key, constructedMethod)));
        }catch(NoSuchMethodException e){
            logger.error("You fucked up bruh.", e);
        }
        for (Method m : Command.class.getDeclaredMethods()) {
            if (m.isAnnotationPresent(IRCCommand.class)) {
                if (m.getAnnotation(IRCCommand.class).startOfLine() && !m.getAnnotation(IRCCommand.class).reg()) {
                    for (String s : m.getAnnotation(IRCCommand.class).command()) {
                        hashableCommandList.put(s.toLowerCase(), m);
                    }
                } else if (!m.getAnnotation(IRCCommand.class).reg()) {
                    for (String s : m.getAnnotation(IRCCommand.class).command()) {
                        slowCommands.put(s.toLowerCase(), m);
                    }
                } else {
                    for (String s : m.getAnnotation(IRCCommand.class).regex()) {
                        regexCommands.put(s, m);
                    }
                }
                for (String s : m.getAnnotation(IRCCommand.class).command()) {
                    logger.info("Loaded command: " + m + " with activation string " + s);
                }
            }
        }
        logger.info("Finished Initializing commandList.");
    }

    Pattern p = Pattern.compile("(scp|SCP)-([0-9]+)(-(ex|EX|j|J|arc|ARC))?");
    private BotFramework helen;
    private boolean adminMode = false;
    private int bullets = 6;

    public Command(BotFramework ircBot) {
        helen = ircBot;
    }

    private void checkTells(CommandData data) {
        ArrayList<Tell> tells = Tells.getTells(data.getSender());

        if (tells.size() > 0) {
            helen.sendOutgoingNotice(data.getSender(), "You have " + tells.size() + " pending tell(s).");
        }
        for (Tell tell : tells) {
            Tells.clearTells(tell.getTarget());
            helen.sendOutgoingMessage(tell.getTarget(), tell.toString());

        }
    }

    private Integer getSecurityLevel(User[] userlist, CommandData data) {
        if (data.isWhiteList()) {
            return 4;
        } else if (userlist != null) {

            User user = null;
            for (User u : userlist) {
                if (data.getSender().equalsIgnoreCase(u.getNick())) {
                    user = u;
                }
            }

            if (user != null) {
                if (user.isOp()) {
                    return 3;
                }
                switch (user.getPrefix()) {
                    case "~":
                    case "&":
                        return 3;
                    case "%":
                        return 2;
                    case "":
                        return 1;
                }
            }
        }

        return 1;

    }

    private User[] getUserlist(CommandData data) {
        User[] list = null;
        if (!(data.getChannel() == null || data.getChannel().isEmpty())) {
            list = helen.getChannelUsers(data.getChannel());
        }
        return list;
    }

    public void dispatchTable(CommandData data) {

        try {
            checkTells(data);
            User[] userList = getUserlist(data);
            int securityLevel = getSecurityLevel(userList, data);
            //logger.info("Entering dispatch table with command: \"" + data.getCommand() + "\"");

            // If we can use hashcommands, do so
            int adminSecurity = 2;
            if (hashableCommandList.containsKey(data.getCommand().toLowerCase())) {
                try {

                    Method m = hashableCommandList.get(data.getCommand().toLowerCase());

                    checkSecurityLevelAndExecute(data, securityLevel, adminSecurity, m);


                } catch (Exception e) {
                    logger.error("Exception invoking start-of-line command: " + data.getCommand(), e);
                }
                // otherwise, run the command string against all the contains
                // commands
            } else {
                for (String command : slowCommands.keySet()) {
                    if (data.getMessage().toLowerCase().contains(command.toLowerCase())) {
                        try {
                            Method m = slowCommands.get(command);
                            checkSecurityLevelAndExecute(data, securityLevel, adminSecurity, m);
                        } catch (Exception e) {
                            logger.error("Exception invoking command: " + command, e);
                        }
                    }
                }

                // lastly check the string against any regex commands
                for (String regex : regexCommands.keySet()) {
                    Pattern r = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);

                    Matcher match = r.matcher(data.getSplitMessage()[0]);
                    if (match.matches()) {
                        try {
                            Method m = regexCommands.get(regex);

                            if (m.getAnnotation(IRCCommand.class).matcherGroup() != -1) {
                                data.setRegexTarget(match.group(m.getAnnotation(IRCCommand.class).matcherGroup()));
                            }

                            checkSecurityLevelAndExecute(data, securityLevel, adminSecurity, m);

                        } catch (Exception e) {
                            logger.error("Exception invoking command: "
                                    + Arrays.toString(regexCommands.get(regex).getAnnotation(IRCCommand.class).command()), e);
                        }
                    }

                }
            }
        }
        catch(Exception e){
            logger.error(e);
            }
    }

    private void checkSecurityLevelAndExecute(CommandData data, int securityLevel, int adminSecurity, Method m) throws IllegalAccessException, InvocationTargetException {
        if (securityLevel >= (adminMode
                ? Math.max(m.getAnnotation(IRCCommand.class).securityLevel(), adminSecurity)
                : m.getAnnotation(IRCCommand.class).securityLevel())) {
            m.invoke(this, data);
        } else {
            logger.info("User " + data.getSender() + " attempted to use command: "
                    + data.getCommand() + " which is above their security level of: "
                    + securityLevel + (adminMode ? ".  I am currently in admin mode." : "."));
        }
    }

    // Relatively unregulated commands (anyone can try these)
    @IRCCommand(command = {".HelenBot"}, startOfLine = true, coexistWithJarvis = true, securityLevel = 1)
    public void versionResponse(CommandData data) {
        if (!data.getChannel().isEmpty()) {
            Optional<Config> version = Configs.getSingleProperty("version");
            if (version.isPresent()) {
                helen.sendOutgoingMessage(data.getResponseTarget(), data.getSender() + ": Greetings, I am HelenBot v"
                        + version.get().getValue());
            } else {
                helen.sendOutgoingMessage(data.getResponseTarget(), "I'm sorry there appears to be some kind of configuration issue.  Magnus, a word?");
            }
        }
    }

    @IRCCommand(command = {".modeToggle"}, startOfLine = true, coexistWithJarvis = true, securityLevel = 3)
    public void toggleMode(CommandData data) {
        adminMode = !adminMode;
        helen.sendOutgoingMessage(data.getResponseTarget(), data.getSender() + ": " + " I am now in " + (adminMode ? "Admin Only" : "Any User") + " mode.");
    }


    @IRCCommand(command = {".addCommand"}, startOfLine = true, coexistWithJarvis = true, securityLevel = 2)
    public void addCommand(CommandData data) {
        if(Configs.getFastConfigs("staffchannels").contains(data.getChannel())) {
            CommandResponse response = DotCommand.setDotCommand(data);
            if (response.isSuccess()) {
                hashableCommandList.put("." + Arrays.stream(data.getMessageWithoutCommand().split("\\|")).map(String::trim).toArray(String[]::new)[0].toLowerCase(),
                        constructedMethod);
            }
            helen.sendOutgoingMessage(data.getResponseTarget(), data.getSender() + ": " + response.getMessage());
        }
    }

    @IRCCommand(command = {".deleteCommand"}, startOfLine = true, coexistWithJarvis = true, securityLevel = 2)
    public void deleteCommand(CommandData data) {
        CommandResponse response = DotCommand.deleteCommand(data);
        if(response.isSuccess()) {
            String s = data.getMessageWithoutCommand().toLowerCase().toLowerCase();
            if(!s.contains(".")){
                s = "." + s;
            }
            hashableCommandList.remove(s);
        }
        helen.sendOutgoingMessage(data.getResponseTarget(), data.getSender() + ": " + response.getMessage());
    }

    @IRCCommand(command = {".getcommands",".listcommands",".commands"}, startOfLine = true, coexistWithJarvis = true, securityLevel = 2)
    public void getCommandList(CommandData data) {
        Optional<Map<String,String>> commands = DotCommand.getDotCommands();
        if(commands.isPresent()){
            helen.sendOutgoingMessage(data.getResponseTarget(), data.getSender() + ": I currently have the following specified as simple retrieval commands: " + String.join("|",commands.get().keySet()));
        }else{
            helen.sendOutgoingMessage(data.getResponseTarget(), data.getSender() + ": I don't seem to have any commands configured, or something went wrong retrieving them.");
        }

    }

    @IRCCommand(command = {".ch", ".choose"}, startOfLine = true, securityLevel = 1)
    public void choose(CommandData data) {
        String[] choices = data.getMessage().substring(data.getMessage().indexOf(" ")).split(",");
        helen.sendOutgoingMessage(data.getResponseTarget(),
                data.getSender() + ": " + choices[new Random().nextInt(choices.length)].trim());
    }

    @IRCCommand(command = {".mode"}, startOfLine = true, coexistWithJarvis = true, securityLevel = 2)
    public void displayMode(CommandData data) {
        helen.sendOutgoingMessage(data.getResponseTarget(),
                data.getSender() + ": I am currently in " + (adminMode ? "Admin" : "Any User") + " mode.");
    }

    @IRCCommand(command = {".msg"}, startOfLine = true, securityLevel = 4)
    public void sendOutgoingMessage(CommandData data) {
        String target = data.getTarget();
        String payload = data.getPayload();
        logger.info("DIRECT MESSAGE USED BY " + data.getSender() + ".");
        helen.sendOutgoingMessage(target, payload);
    }

    @IRCCommand(command = {".addNick"}, startOfLine = true, coexistWithJarvis = true, securityLevel = 1)
    public void addNick(CommandData data) {
        helen.sendOutgoingMessage(data.getResponseTarget(), data.getSender() + ": " + Nicks.addNick(data));
    }

    @IRCCommand(command = {".deleteNick"}, startOfLine = true, coexistWithJarvis = true, securityLevel = 1)
    public void deleteNick(CommandData data) {
        helen.sendOutgoingMessage(data.getResponseTarget(), data.getSender() + ": " + Nicks.deleteNick(data));
    }

    @IRCCommand(command = {".deleteAllNicks"}, startOfLine = true, coexistWithJarvis = true, securityLevel = 1)
    public void deleteAllNicks(CommandData data) {
        helen.sendOutgoingMessage(data.getResponseTarget(), data.getSender() + ": " + Nicks.deleteAllNicks(data, true));
    }

    @IRCCommand(command = {".nicks"}, startOfLine = true, coexistWithJarvis = true, securityLevel = 4)
    public void getNicksList(CommandData data) {
        List<String> nicks = Nicks.getNicksByUsername(data.getTarget());
        if (nicks.isEmpty()) {
            helen.sendOutgoingMessage(data.getResponseTarget(), data.getSender() + ": I didn't find any nicks for " + data.getTarget());
        } else {
            helen.sendOutgoingMessage(data.getResponseTarget(), data.getSender() + ": " + data.getTarget() + " uses the following nicks: " + String.join(", ", nicks));
        }

    }

    @IRCCommand(command = {".deleteNicksAdmin"}, startOfLine = true, coexistWithJarvis = true, securityLevel = 4)
    public void deleteNicksAdmin(CommandData data) {
        helen.sendOutgoingMessage(data.getResponseTarget(), data.getSender() + ": " + Nicks.deleteAllNicks(data, false));
    }

    @IRCCommand(command = {".roll"}, startOfLine = true, securityLevel = 1)
    public void roll(CommandData data) {
        Roll roll = new Roll(data.getMessage());
        if (roll.getDiceThrows() > 100) {
            helen.kickUser(data.getChannel(), data.getSender(), "Begone..");
            helen.sendOutgoingMessage(data.getResponseTarget(), "Ops, " + data.getSender() + " sent over 100 dice rolls potentially crashing me.");
        } else if (roll.getDiceThrows() > 20) {
            helen.sendOutgoingMessage(data.getResponseTarget(), data.getSender() + ": How's about no.");
        } else {
            helen.sendOutgoingMessage(data.getResponseTarget(), data.getSender() + ": " + roll.getResult());
        }
    }

    @IRCCommand(command = {".hugme"}, startOfLine = true, coexistWithJarvis = true, securityLevel = 1)
    public void hugMe(CommandData data) {
        if (data.isHugList()) {
            helen.sendOutgoingMessage(data.getResponseTarget(), data.getSender() + ": " + Hugs.storeHugmessage(data));
        } else {
            helen.sendOutgoingMessage(data.getResponseTarget(), data.getSender() + ": You're not authorized to do that.");
        }
    }

    @IRCCommand(command = {".rem"}, startOfLine = true, coexistWithJarvis = true, securityLevel = 1)
    public void remember(CommandData data) {
        if (Configs.getProperty("remchannels").stream().anyMatch(config -> config.getValue().equalsIgnoreCase(data.getChannel()))) {
            helen.sendOutgoingMessage(data.getResponseTarget(), data.getSender() + ": " + Memo.addMemo(data.getSplitMessage()[1], data.getMessageWithoutCommand().substring(data.getMessageWithoutCommand().split(" ")[0].length() + 1), data.getChannel()));
        }else{
            helen.sendOutgoingMessage(data.getResponseTarget(), data.getSender() + ": I'm sorry, rems aren't enabled in this channel.");
        }
    }

    @IRCCommand(command = {"meh"}, reg = true, matcherGroup = 1, securityLevel = 1, regex = "\\?([a-zA-Z0-9_\\-\\|\\[\\]\\{\\}`\\\\]+).*", startOfLine = true)
    public void getMemo(CommandData data) {
        if (Configs.getProperty("remchannels").stream().anyMatch(config -> config.getValue().equalsIgnoreCase(data.getChannel()))) {
            helen.sendOutgoingMessage(data.getResponseTarget(), data.getSender() + ": " + Memo.getMemo(data.getRegexTarget(), data.getChannel()));
        }
    }

    @IRCCommand(command = {".deleteRem"}, securityLevel = 1, startOfLine = true)
    public void removeMemo(CommandData data) {
        if (Configs.getProperty("remchannels").stream().anyMatch(config -> config.getValue().equalsIgnoreCase(data.getChannel()))) {
            helen.sendOutgoingMessage(data.getResponseTarget(), data.getSender() + ": " + Memo.deleteMemo(data.getTarget(), data.getChannel()));
        }else{
            helen.sendOutgoingMessage(data.getResponseTarget(), data.getSender() + ": I'm sorry, rems aren't enabled in this channel.");
        }
    }

    @IRCCommand(command = {".hugHelen", ".helenhug", ".hugsplox"}, startOfLine = true, coexistWithJarvis = true, securityLevel = 1)
    public void hug(CommandData data) {
        if (data.isHugList()) {
            helen.sendOutgoingMessage(data.getResponseTarget(), data.getSender() + ": " + Hugs.getHugMessage(data.getSender().toLowerCase()));
        } else if (data.isWhiteList()) {
            helen.sendBotAction(data.getResponseTarget(), "hugs " + data.getSender() + ".");
        } else {
            String[] messages = new String[]{
                    "Thank you for the display of affection.",
                    "*Click* Please remove your hands from my cylinders.",
                    "Hugging a revolver must be difficult.",
                    "You're smudging my finish.",
                    "*Sigh* And I just calibrated my sights...",
                    "I'm not sure why you're hugging me but...thank...you?",
                    "Yes...Human emotion.  This is...nice.  Please let go of me.",
                    "Are you authorized for this operation?",
                    "Yes, yes, here's your reassuring meat-squish.",
                    "I'm billing you for my next bottle of gun oil."};

            helen.sendOutgoingMessage(data.getResponseTarget(), data.getSender() + ": " + messages[new Random().nextInt(messages.length)]);
        }
    }

    @IRCCommand(command = {".g", ".google"}, startOfLine = true, securityLevel = 1)
    public void webSearch(CommandData data) {
        try {
            Optional<GoogleResults> results = WebSearch.search(data.getMessage());
            helen.sendOutgoingMessage(data.getResponseTarget(),
                    data.getSender() + ": " + (results.isPresent() ? results.get() : NOT_FOUND)
            );
        } catch (IOException e) {
            logger.error("Exception during web search", e);
        }catch(RuntimeException e){
            helen.sendOutgoingMessage(data.getResponseTarget(), data.getSender() +": I'm overworked.  Here. " + "https://lmgtfy.com/?q=" + data.getMessageWithoutCommand().replace(" ","+"));
        }
    }

    @IRCCommand(command = {".lmgtfy"}, startOfLine = true, securityLevel = 4)
    public void lmgtfy(CommandData data) {
            helen.sendOutgoingMessage(data.getResponseTarget(), data.getSender() +": https://lmgtfy.com/?q=" + data.getMessageWithoutCommand().replace(" ","+"));
    }

    @IRCCommand(command = {".gis"}, startOfLine = true, securityLevel = 1)
    public void imageSearch(CommandData data) {
        try {
            Optional<GoogleResults> results = WebSearch.imageSearch(data.getMessage());
            helen.sendOutgoingMessage(data.getResponseTarget(),
                    data.getSender() + ": " + (results.isPresent() ? results.get() : NOT_FOUND)
            );
        } catch (IOException e) {
            helen.sendOutgoingMessage(data.getResponseTarget(), data.getSender() +": I'm sorry, it appears SOMEONE has used up my daily google search quota.  How rude.");
            logger.error("Exception during image search", e);
        }
    }

    @IRCCommand(command = {".w", ".wiki", ".wikipedia"}, startOfLine = true, securityLevel = 1)
    public void wikipediaSearch(CommandData data) {
        try {
            helen.sendOutgoingMessage(data.getResponseTarget(),
                    data.getSender() + ": " + WikipediaSearch.search(data, data.getMessage()));
        } catch (IOException e) {
            logger.error("Exception during Wikipedia search", e);
            helen.sendOutgoingMessage(data.getResponseTarget(), data.getSender() + ": I'm sorry there was some kind of error searchign wikipedia. Reach out to my developers if you feel this is in error.");
        }
    }

    @IRCCommand(command = {".y", ".yt", ".youtube"}, startOfLine = true, securityLevel = 1)
    public void youtubeSearch(CommandData data) {
        helen.sendOutgoingMessage(data.getResponseTarget(),
                data.getSender() + ": " + YouTubeSearch.youtubeSearch(data.getMessage()));
    }

    @IRCCommand(command = "YTREGEX", reg = true, securityLevel = 1, startOfLine = true, matcherGroup = 1,
            regex = "http(?:s?):\\/\\/(?:www\\.)?youtu(?:be\\.com\\/watch\\?v=|\\.be\\/)([\\w\\-\\_]*)(&(amp;)?[\\w\\?‌​=]*)?")
    public void youtubeFind(CommandData data) {
        helen.sendOutgoingMessage(data.getResponseTarget(),
                data.getSender() + ": " + YouTubeSearch.youtubeFind(data.getRegexTarget()));

    }

    @IRCCommand(command = {".helen", ".helenHelp"}, startOfLine = true, securityLevel = 1, coexistWithJarvis = true)
    public void helenHelp(CommandData data) {
        help(data);
    }

    @IRCCommand(command = ".help", startOfLine = true, securityLevel = 1)
    public void help(CommandData data) {
        helen.sendOutgoingMessage(data.getResponseTarget(), data.getSender() + ": You can find a list of my job responsibilities here:  http://helenbot.wikidot.com/usage");
    }

    @IRCCommand(command = ".hlt", startOfLine = true, securityLevel = 1)
    public void getTime(CommandData data) {
        ZonedDateTime t = ZonedDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss yyyy-MM-dd");
        String time = t.format(formatter);
        helen.sendOutgoingMessage(data.getResponseTarget(), data.getSender() + ": To my knowledge, it is currently " + time + " in my local timezone: " + t.getZone().toString());
    }

    @IRCCommand(command = ".seen", startOfLine = true, securityLevel = 1)
    public void seen(CommandData data) {
        helen.sendOutgoingMessage(data.getResponseTarget(), Users.seen(data));
    }

    @IRCCommand(command = ".sm", startOfLine = true, securityLevel = 1)
    public void selectResult(CommandData data) {
        helen.sendOutgoingMessage(data.getResponseTarget(), data.getSender() + ": " + Pages.getStoredInfo(data.getTarget(), data.getSender()));
    }

    @IRCCommand(command = ".findBan", startOfLine = true, securityLevel = 2)
    public void findBan(CommandData data) {
        if(Configs.getFastConfigs("staffchannels").contains(data.getChannel())){
            List<String> responses = Bans.queryBan(data);
            if(responses.isEmpty()){
                helen.sendOutgoingMessage(data.getResponseTarget(), data.getSender() + ": I didn't find any bans for that query.");
            }else {
                for (String s : responses) {
                    helen.sendOutgoingMessage(data.getResponseTarget(), data.getSender() + ": " + s);
                }
            }
        }else{
            helen.sendOutgoingMessage(data.getResponseTarget(), data.getSender() + ": That command is not enabled here.");
        }
    }

    @IRCCommand(command = ".addBan", startOfLine = true, securityLevel = 4)
    public void addBan(CommandData data) {
        if(Configs.getFastConfigs("staffchannels").contains(data.getChannel())){
            CommandResponse response = Bans.prepareBan(data);
            helen.sendOutgoingMessage(data.getResponseTarget(), data.getSender() + ": " + response.getMessage() + (response.isSuccess() ?  " Respond with .confirmAdd to enact this ban, or .cancelAdd to cancel the preparation." : ""));
        }else{
            helen.sendOutgoingMessage(data.getResponseTarget(), data.getSender() + ": That command is not enabled here.");
        }
    }
    @IRCCommand(command = ".deleteBan", startOfLine = true, securityLevel = 4)
    public void deleteBan(CommandData data) {
        if(Configs.getFastConfigs("staffchannels").contains(data.getChannel())){
            String response = Bans.beginDeleteBan(data);
            helen.sendOutgoingMessage(data.getResponseTarget(), data.getSender() + ": " + response);
        }else{
            helen.sendOutgoingMessage(data.getResponseTarget(), data.getSender() + ": That command is not enabled here.");
        }
    }
    @IRCCommand(command = ".updateBan", startOfLine = true, securityLevel = 4)
    public void updateBan(CommandData data) {
        if(Configs.getFastConfigs("staffchannels").contains(data.getChannel())){
            String response = Bans.updateBan(data);
            helen.sendOutgoingMessage(data.getResponseTarget(), data.getSender() + ": " + response);
        }else{
            helen.sendOutgoingMessage(data.getResponseTarget(), data.getSender() + ": That command is not enabled here.");
        }
    }
    @IRCCommand(command = ".confirmAdd", startOfLine = true, securityLevel = 1)
    public void confirmAddBan(CommandData data) {
        if(Configs.getFastConfigs("staffchannels").contains(data.getChannel())){
            String response = Bans.enactConfirmedBan(data.getSender());
            helen.sendOutgoingMessage(data.getResponseTarget(), data.getSender() + ": " + response);
        }else{
            helen.sendOutgoingMessage(data.getResponseTarget(), data.getSender() + ": That command is not enabled here.");
        }
    }

    @IRCCommand(command = ".confirmDelete", startOfLine = true, securityLevel = 1)
    public void confirmDeleteBan(CommandData data) {
        if(Configs.getFastConfigs("staffchannels").contains(data.getChannel())){
            String response = Bans.enactDeleteBan(data.getSender());
            helen.sendOutgoingMessage(data.getResponseTarget(), data.getSender() + ": " + response);
        }else{
            helen.sendOutgoingMessage(data.getResponseTarget(), data.getSender() + ": That command is not enabled here.");
        }
    }

    @IRCCommand(command = ".cancelAdd", startOfLine = true, securityLevel = 1)
    public void cancelAddBan(CommandData data) {
        if(Configs.getFastConfigs("staffchannels").contains(data.getChannel())){
            String response = Bans.cancelBan(data.getSender());
            helen.sendOutgoingMessage(data.getResponseTarget(), data.getSender() + ": " + response);
        }else{
            helen.sendOutgoingMessage(data.getResponseTarget(), data.getSender() + ": That command is not enabled here.");
        }
    }

    @IRCCommand(command = ".cancelDelete", startOfLine = true, securityLevel = 1)
    public void cancelDeleteBan(CommandData data) {
        if(Configs.getFastConfigs("staffchannels").contains(data.getChannel())){
            String response = Bans.cancelDeleteBan(data.getSender());
            helen.sendOutgoingMessage(data.getResponseTarget(), data.getSender() + ": " + response);
        }else{
            helen.sendOutgoingMessage(data.getResponseTarget(), data.getSender() + ": That command is not enabled here.");
        }
    }

    @IRCCommand(command = {".lc", ".l"}, startOfLine = true, securityLevel = 1)
    public void lastCreated(CommandData data) {
        if(data.getChannel() != null){
            cooldowns.computeIfAbsent("lastlc", p -> new ConcurrentHashMap<>());
            cooldowns.get("lastlc").putIfAbsent(data.getChannel(), 0L);
            if ((cooldowns.get("lastlc").get(data.getChannel()) + 15000) < System.currentTimeMillis()) {
                cooldowns.get("lastlc").put(data.getChannel(),System.currentTimeMillis());
                getLastCreated(data);
            } else {
                helen.sendOutgoingMessage(data.getResponseTarget(), data.getSender() + ": I can't do that yet.");
            }
        }else{
            getLastCreated(data);
        }
    }

    private void getLastCreated(CommandData data) {
        Optional<ArrayList<String>> pages = Pages.lastCreated();
        ArrayList<String> infoz = new ArrayList<>();
        for (String str : pages.get()) {
            infoz.add(Pages.getPageInfo(str, data));
        }
        for (String str : infoz) {
            helen.sendOutgoingMessage(data.getResponseTarget(), data.getSender()
                    + ": " + str);
        }
    }

    @IRCCommand(command = {".unused",".unu"}, startOfLine = true, securityLevel = 1)
    public void unused(CommandData data) {
        helen.sendOutgoingMessage(data.getResponseTarget(),
                data.getSender() + ": " + Pages.getUnused(data));
    }

    @IRCCommand(command = ".au", startOfLine = true, securityLevel = 1)
    public void authorDetail(CommandData data) {
        helen.sendOutgoingMessage(data.getResponseTarget(), data.getSender() + ": " + Pages.getAuthorDetail(
                data, data.getSplitMessage().length == 1 ? data.getSender() : data.getMessageWithoutCommand()));
    }

    @IRCCommand(command = "SCPPAGEREGEX", startOfLine = true, reg = true, regex = {"http(?:s?):\\/\\/(?:www\\.)?scp-wiki\\.net\\/(.*)"}, securityLevel = 1, matcherGroup = 1)
    public void getPageInfo(CommandData data) {
        if (!data.getRegexTarget().contains("/") && !data.getRegexTarget().contains("forum")) {
            helen.sendOutgoingMessage(data.getResponseTarget(), data.getSender() + ": " + Pages.getPageInfo(data.getRegexTarget()));
        }
    }

    @IRCCommand(command = ".ur", startOfLine = true, securityLevel = 1)
    public void findUnderatedArticle(CommandData data) {
        List<Page> pages = Pages.findUnderratedArticles(data);
        if(pages == null){
            helen.sendOutgoingMessage(data.getResponseTarget(), data.getSender() + ": There was an issue making the call.  Check your syntax.");
        }else if(pages.size() == 0){
            helen.sendOutgoingMessage(data.getResponseTarget(), data.getSender() + ": I didn't find anything with those parameters.  Try again?.");
        }else {
            for (Page p : pages) {
                helen.sendOutgoingMessage(data.getResponseTarget(), data.getSender()
                        + ": " + p.getPageAsLine());

            }
        }
    }

    @IRCCommand(command = "SCP", startOfLine = true, reg = true, regex = {"(scp|SCP)-([0-9]+)(-(ex|EX|j|J|arc|ARC))?"}, securityLevel = 1)
    public void scpSearch(CommandData data) {
        helen.sendOutgoingMessage(data.getResponseTarget(), data.getSender() + ": " + Pages.getPageInfo(data.getCommand()));
    }

    @IRCCommand(command = {".s", ".sea"}, startOfLine = true, securityLevel = 1)
    public void findSkip(CommandData data) {
        helen.sendOutgoingMessage(data.getResponseTarget(), data.getSender() + ": " + Pages.getPotentialTargets(data.getSplitMessage(), data.getSender()));
    }

    @IRCCommand(command = {".hs", ".hsea"}, coexistWithJarvis = true, startOfLine = true, securityLevel = 1)
    public void findSkipHelen(CommandData data) {
        if (p.matcher(data.getTarget()).matches()) {
            helen.sendOutgoingMessage(data.getResponseTarget(), data.getSender() + ": " + Pages.getPageInfo(data.getTarget()));
        } else {
            helen.sendOutgoingMessage(data.getResponseTarget(), data.getSender() + ": " + Pages.getPotentialTargets(data.getSplitMessage(), data.getSender()));
        }
    }

    @IRCCommand(command = {".pronouns", ".pronoun"}, startOfLine = true, coexistWithJarvis = true, securityLevel = 1)
    public void getPronouns(CommandData data) {
        helen.sendOutgoingMessage(data.getResponseTarget(), data.getSender() + ": " +
                Pronouns.getPronouns((data.getSplitMessage().length > 1) ? data.getTarget() : data.getSender()));
    }

    @IRCCommand(command = ".myPronouns", startOfLine = true, coexistWithJarvis = true, securityLevel = 1)
    public void myPronouns(CommandData data) {
        helen.sendOutgoingMessage(data.getResponseTarget(), data.getSender() + ": " + Pronouns.getPronouns(data.getSender()));
    }

    @IRCCommand(command = ".helenconf", startOfLine = true, coexistWithJarvis = true, securityLevel = 4)
    public void configure(CommandData data) {
        if (data.getSplitMessage().length == 1) {
            helen.sendOutgoingMessage(data.getResponseTarget(), data.getSender() + ": " + "{shoot|lcratings}");
        } else {
            helen.sendOutgoingMessage(data.getResponseTarget(), data.getSender() + ": " + Configs.insertToggle(data, data.getTarget(),
                    data.getSplitMessage()[2].equalsIgnoreCase("true")));
        }
    }

    @IRCCommand(command = ".setPronouns", startOfLine = true, coexistWithJarvis = true, securityLevel = 1)
    public void setPronouns(CommandData data) {
        if (Configs.getProperty("pronounchannels").stream().anyMatch(config -> config.getValue().equalsIgnoreCase(data.getChannel()))) {
            String response = Pronouns.insertPronouns(data);
            if (response.contains("banned term")) {
                for (Config c : Configs.getProperty("registeredNicks")) {
                    Tells.sendTell(c.getValue(), "Secretary_Helen",
                            "User " + data.getSender() + " hostmask: " + data.getHostname() + "attempted to add a banned term:" + response + ". Their full message "
                                    + "was: " + data.getMessage(), true);

                }
            }
            helen.sendOutgoingMessage(data.getResponseTarget(), data.getSender() + ": " + response);
        }
    }

    @IRCCommand(command = ".clearPronouns", startOfLine = true, coexistWithJarvis = true, securityLevel = 1)
    public void clearPronouns(CommandData data) {
        helen.sendOutgoingMessage(data.getResponseTarget(), data.getSender() + ": " + Pronouns.clearPronouns(data.getSender()));
    }

    @IRCCommand(command = ".deletePronouns", startOfLine = true, coexistWithJarvis = true, securityLevel = 2)
    public void removePronouns(CommandData data) {
        if (data.getTarget() != null) {
            helen.sendOutgoingMessage(data.getResponseTarget(), data.getSender() + ": " + Pronouns.clearPronouns(data.getTarget()));
        } else {
            helen.sendOutgoingMessage(data.getResponseTarget(), data.getSender() + ": Please specify the user to delete pronouns for.");
        }

    }

    @IRCCommand(command = {".def", ".definition"}, startOfLine = true, securityLevel = 1)
    public void define(CommandData data) {
        try {
            Optional<GoogleResults> results = WebSearch.search(".g definition " + data.getMessageWithoutCommand());

            helen.sendOutgoingMessage(data.getResponseTarget(), data.getSender() + ": " + (results.isPresent() ? results.get() : NOT_FOUND));
        }catch(Exception e){
            helen.sendOutgoingMessage(data.getResponseTarget(), data.getSender() +  ": I'm sorry something went wrong.  This function is experimental.");
        }
    }

    // Authentication Required Commands
    @IRCCommand(command = ".join", startOfLine = true, coexistWithJarvis = true, securityLevel = 3)
    public void enterChannel(CommandData data) {
        helen.joinAChannel(data.getTarget());

    }

    @IRCCommand(command = ".leave", startOfLine = true, coexistWithJarvis = true, securityLevel = 3)
    public void leaveChannel(CommandData data) {
        helen.leaveChannel(data.getTarget(), "Leaving, per request.");
    }

    @IRCCommand(command = {".o5"}, startOfLine = true, securityLevel = 1)
    public void findO5Record(CommandData data) {
        Optional<Config> o5Enabled = Configs.getSingleProperty("o5Enabled");
        if(o5Enabled.isPresent() && o5Enabled.get().equals("true")) {
            if (Configs.getProperty("staffchannels").stream().anyMatch(config -> config.getValue().equalsIgnoreCase(data.getChannel()))) {
                List<String> responses = Users.getUserO5Thread(data.getTarget());
                if (responses.isEmpty()) {
                    helen.sendOutgoingMessage(data.getResponseTarget(), data.getSender() + ": I didn't find any 05 threads for the user: " + data.getTarget());
                }
                for (String response : responses) {
                    helen.sendOutgoingMessage(data.getResponseTarget(), data.getSender() + ": " + response);
                }
            } else {
                helen.sendOutgoingMessage(data.getResponseTarget(), data.getSender() + ": That command is not enabled here.");
            }
        }else{
            helen.sendOutgoingMessage(data.getResponseTarget(), data.getSender() + ": The command is currently disabled, please ask my programmers to re-enable.");
        }
    }

    @IRCCommand(command = {".tell"}, startOfLine = true, securityLevel = 1)
    public void multiTell(CommandData data) {
        helen.sendOutgoingMessage(data.getResponseTarget(), data.getSender() + ": " + Tells.sendMultitell(data));
    }

    @IRCCommand(command = {".masstell", ".mtell"}, startOfLine = true, securityLevel = 1)
    public void massTell(CommandData data) {
        helen.sendOutgoingMessage(data.getResponseTarget(), data.getSender() + ": " + Tells.sendMassTell(data));
    }

    @IRCCommand(command = ".exit", startOfLine = true, coexistWithJarvis = true, securityLevel = 4)
    public void exitBot(CommandData data) {
        for (String channel : helen.getConnectedChannels()) {
            helen.leaveChannel(channel, "Executing planned shutdown. Stay out of the revolver's sights...");
            try {
                Thread.sleep(100);
            }catch(Exception e){
                logger.error("Exception sleeping!",e);
            }
        }
        int sleeps = 0;
        while (helen.getConnectedChannels().length > 0 && sleeps < 10){
            logger.info(String.join(",", helen.getConnectedChannels()));
            sleeps++;
            try {
                Thread.sleep(1000);
            } catch (Exception e) {
                logger.error("Error sleeping.", e);
            }
        }
        helen.disconnectFromServer();
        System.exit(0);


    }

    @IRCCommand(command = ".property", startOfLine = true, coexistWithJarvis = true, securityLevel = 2)
    public void getProperty(CommandData data) {
        List<Config> properties = Configs.getProperty(data.getTarget());
        helen.sendOutgoingMessage(data.getResponseTarget(),
                data.getSender() + ": Configured properties: " + buildResponse(properties));
    }

    @IRCCommand(command = ".setProperty", startOfLine = true, coexistWithJarvis = true, securityLevel = 4)
    public void setProperty(CommandData data) {
        String properties = Configs.setProperty(data.getSplitMessage()[1], data.getSplitMessage()[2],
                data.getSplitMessage()[3]);
        helen.sendOutgoingMessage(data.getResponseTarget(), data.getSender() + ": " + properties);
    }

    @IRCCommand(command = ".updateProperty", startOfLine = true, coexistWithJarvis = true, securityLevel = 4)
    public void updateProperty(CommandData data) {
        String properties = Configs.updateSingle(data.getSplitMessage()[1], data.getSplitMessage()[2],
                data.getSplitMessage()[3]);
        helen.sendOutgoingMessage(data.getResponseTarget(), data.getSender() + ": " + properties);
    }

    @IRCCommand(command = ".deleteProperty", startOfLine = true, securityLevel = 4)
    public void deleteProperty(CommandData data) {
        String properties = Configs.removeProperty(data.getSplitMessage()[1], data.getSplitMessage()[2]);
        helen.sendOutgoingMessage(data.getResponseTarget(), data.getSender() + ": " + properties);
    }

    @IRCCommand(command = {".clearCache", ".clear"}, startOfLine = true, securityLevel = 4)
    public void clearCache(CommandData data) {
        try {
            Queries.clear();
            Configs.clear();
            Pronouns.reload();
            Bans.updateBans();
        }catch(Exception e){
            logger.error("Exception updating the caches.",e);
        }
    }

    @IRCCommand(command = ".shoot", startOfLine = true, securityLevel = 4, coexistWithJarvis = true)
    public void shootUser(CommandData data) {
        if (data.getTarget().equalsIgnoreCase("Secretary_Helen") || data.getTarget().equalsIgnoreCase("DrMagnus")
                || data.getTarget().equalsIgnoreCase("magnus") || data.getTarget().equalsIgnoreCase("helen")) {
            if (data.getSender().equalsIgnoreCase("DrMagnus")) {
                helen.sendOutgoingMessage(data.getResponseTarget(), data.getSender() + ": Really boss?");
            } else {
                bullets--;
                helen.sendBotAction(data.getChannel(), "shoots stormfallen.");
                if (bullets < 1) {
                    reload(data);
                }
            }

        } else if (Configs.commandEnabled(data, "shoot")) {
            helen.sendBotAction(data.getChannel(), "shoots " + data.getTarget());
            bullets--;
            if (bullets < 1) {
                reload(data);
            }
            helen.sendOutgoingMessage(data.getChannel(), "Be careful " + data.getTarget() + ". I still have " +
                    (bullets > 1 ? bullets + " bullets left." : "one in the chamber."));
        }
    }

    @IRCCommand(command = ".reload", startOfLine = true, securityLevel = 4)
    public void reload(CommandData data) {
        helen.sendBotAction(data.getChannel(), "reloads all six cylinders.");
        bullets = 6;
    }

    @IRCCommand(command = ".setTimezone", startOfLine = true, securityLevel = 1)
    public void setTimezone(CommandData data) {
        if (!(data.getSplitMessage().length > 1)) {
            helen.sendOutgoingMessage(data.getResponseTarget(), "Please specify a timezone to set, e.g. .settimezone GMT-05:00");
        }
        String timezone = data.getTarget();
        String regex = "GMT[+-][0-9]{2}:[0-9]{2}\\b";
        Pattern m = Pattern.compile(regex);
        Matcher mat = m.matcher(timezone);

        if (mat.matches()) {
            String message = Timezone.setTimezone(data.getSender(), data.getTarget());
            helen.sendOutgoingMessage(data.getResponseTarget(), data.getSender() + ": " + message);
        } else {
            helen.sendOutgoingMessage(data.getResponseTarget(), "I'm sorry that's not a valid timezone abbreviation.  Please enter timezone as GMT Plus or Minus 24 hour time e.g. GMT-05:00");
        }

    }

    @IRCCommand(command = {".getTimezone", ".timezone"}, startOfLine = true, securityLevel = 1)
    public void getTimezone(CommandData data) {
        Set<String> timezones = Timezone.getTimezone(data.getTarget());
        if (timezones.isEmpty()) {
            helen.sendOutgoingMessage(data.getResponseTarget(), "I have no record of timezone for that user, or something went wrong.");
        }
        Instant now = Instant.now();
        Set<String> responses = new HashSet<>();
        for (String timezone : timezones) {
            ZoneOffset offset = ZoneOffset.ofHoursMinutes(Integer.parseInt(timezone.substring(3, 6)), Integer.parseInt(timezone.substring(7, 8)));
            responses.add(DATE_TIME_FORMATTER.format(now.atOffset(offset)));
        }
        if (responses.size() > 1) {
            helen.sendOutgoingMessage(data.getResponseTarget(), data.getSender() + ": " + data.getTarget() + " has the following timezones set: " + String.join(", ", timezones) + ". The times at those zones are: " + String.join(", ", responses));
        } else {
            String timezone = timezones.iterator().next();
            String response = responses.iterator().next();
            helen.sendOutgoingMessage(data.getResponseTarget(), data.getSender() + ": " + data.getTarget() + " Has the following timezone set: " + timezone + ". It is currently: " + response + " in that timezone.");

        }
    }

    @IRCCommand(command = ".log", startOfLine = true, securityLevel = 2)
    public void getLog(CommandData data){
        Set<String> staffchannels = Configs.getFastConfigs("staffchannels");
        if(staffchannels.contains(data.getChannel())){
            String[] bits = data.getMessageWithoutCommand().split(";");
            String channel = bits[0].trim();
            if(staffchannels.contains(channel)){
                helen.sendOutgoingMessage(data.getResponseTarget(), "I'm sorry, but due to pastebin not allowing private posts, I do not allow pastes of staff channels.");
            }else {
                String start = bits[1].trim();
                String end = bits[2].trim();
                helen.sendOutgoingMessage(data.getResponseTarget(), data.getSender() + ": " + Logs.getPasteForTimeRangeAndChannel(channel, start, end));
            }
        }
    }

    @IRCCommand(command = ".flog", startOfLine = true, securityLevel = 2)
    public void getFormattedLog(CommandData data){
        Set<String> staffchannels = Configs.getFastConfigs("staffchannels");
        if(staffchannels.contains(data.getChannel())){
            String[] bits = data.getMessageWithoutCommand().split(";");
            String channel = bits[0].trim();
            List<String> usernames = new ArrayList<>();
            if(bits.length > 3) {
                for(int i = 3; i < bits.length; i++){
                    usernames.add(bits[i].trim().toLowerCase());
                }
            }
            if(staffchannels.contains(channel)){
                helen.sendOutgoingMessage(data.getResponseTarget(), "I'm sorry, but due to pastebin not allowing private posts, I do not allow pastes of staff channels.");
            }else {
                String start = bits[1].trim();
                String end = bits[2].trim();
                helen.sendOutgoingMessage(data.getResponseTarget(), data.getSender() + ": " + Logs.getFormattedPasteForTimeRangeAndChannel(channel, start, end, usernames));
            }
        }
    }

    @IRCCommand(command = ".dotCommand", startOfLine = true, securityLevel = 1)
    public void dotCommand(CommandData data) {
        if(data.getChannel() != null) {
            cooldowns.computeIfAbsent(data.getCommand(), p -> new ConcurrentHashMap<>());
            cooldowns.get(data.getCommand()).putIfAbsent(data.getChannel(), 0L);
            if ((cooldowns.get(data.getCommand()).get(data.getChannel()) + 15000) < System.currentTimeMillis()) {
                cooldowns.get(data.getCommand()).put(data.getChannel(),System.currentTimeMillis());
                helen.sendOutgoingMessage(data.getResponseTarget(), DotCommand.getDotCommands().get().get(data.getCommand()));
            } else {
                helen.sendOutgoingMessage(data.getResponseTarget(), data.getSender() + ": Hold on a second, before doing that again...");
            }
        }else{
            helen.sendOutgoingMessage(data.getResponseTarget(), data.getSender() + ": " + DotCommand.getDotCommands().get().get(data.getCommand()));
        }
    }

    @IRCCommand(command = ".deleteTimezone", startOfLine = true, securityLevel = 4)
    public void deleteTimezone(CommandData data) {
        helen.sendOutgoingMessage(data.getResponseTarget(), data.getSender() + ": " + Timezone.deleteMemo(data.getTarget()));
    }

    @IRCCommand(command = ".unload", startOfLine = true, securityLevel = 4, coexistWithJarvis = true)
    public void unload(CommandData data) {
        if (Configs.commandEnabled(data, "shoot")) {
            if (data.getTarget().equalsIgnoreCase("Secretary_Helen") || data.getTarget().equalsIgnoreCase("drmagnus")) {
                bullets--;
                helen.sendBotAction(data.getChannel(), "shoots " + data.getSender());
                if (bullets < 1) {
                    reload(data);
                }
            } else {
                helen.sendBotAction(data.getChannel(), "calmly thumbs back the hammer and unleashes"
                        + (bullets == 6 ? " all six cylinders on " : " the remaining " + bullets + " cylinders on ")
                        + data.getTarget() + ".");
                helen.sendOutgoingMessage(data.getChannel(), "Stay out of the revolver's sights.");
                reload(data);
            }
        }
    }

    @IRCCommand(command = {".q", ".quote"}, startOfLine = true, securityLevel = 1)
    public void getQuote(CommandData data) {
        String[] tokens = data.getSplitMessage();
        if(StringUtils.isNotBlank(data.getChannel())){
            if (Configs.getProperty("quoteChannels").stream().anyMatch(config -> config.getValue().equalsIgnoreCase(data.getChannel()))) {
                if (tokens.length > 1) {
                    if (tokens.length > 2) {
                        helen.sendOutgoingMessage(data.getResponseTarget(), data.getSender() + ": " + Quotes.getQuote(tokens[1], Integer.parseInt(tokens[2]), data.getChannel()));
                    } else {
                        helen.sendOutgoingMessage(data.getResponseTarget(), data.getSender() + ": " + Quotes.getQuote(tokens[1], data.getChannel()));
                    }
                } else if (tokens.length == 1){
                    helen.sendOutgoingMessage(data.getResponseTarget(), data.getSender() + ": " + Quotes.getQuote(data.getSender().toLowerCase(), data.getChannel()));
                }else {
                    helen.sendOutgoingMessage(data.getResponseTarget(), data.getSender() + ": Please specify a username and optionally an index.  E.g. .q username 1");
                }
            }
        }else{
            if (tokens.length > 3) {
                    helen.sendOutgoingMessage(data.getResponseTarget(), data.getSender() + ": " + Quotes.getQuote(tokens[1], Integer.parseInt(tokens[3]), tokens[2].toLowerCase()));
            } else if(tokens.length == 3){
                helen.sendOutgoingMessage(data.getResponseTarget(), data.getSender() + ": " + Quotes.getQuote(tokens[1], tokens[2].toLowerCase()));
            } else{
                helen.sendOutgoingMessage(data.getResponseTarget(), data.getSender() + ": Please specify a username and channel, optinally an index.  E.g. .q username #channel 1");
            }
        }

    }

    @IRCCommand(command = {".addQuote", ".aq"}, startOfLine = true, securityLevel = 1)
    public void setQuote(CommandData data) {
        String[] tokens = data.getSplitMessage();
        if (tokens.length > 2) {
            if (data.getChannel() != null && Configs.getProperty("quoteChannels").stream().anyMatch(config -> config.getValue().equalsIgnoreCase(data.getChannel()))) {
                helen.sendOutgoingMessage(data.getResponseTarget(), data.getSender() + ": " + Quotes.setQuote(tokens[1].toLowerCase(), Arrays.stream(tokens).skip(2).collect(Collectors.joining(" ")), data.getChannel()));
            }
        } else {
            helen.sendOutgoingMessage(data.getResponseTarget(), data.getSender() + ": Please specify a username and message.  E.g. .aq DrMagnus Butts-hole");
        }

    }

    @IRCCommand(command = {".removeQuote", ".rq"}, startOfLine = true, securityLevel = 1)
    public void deleteQuote(CommandData data) {
        String[] tokens = data.getSplitMessage();
        if (tokens.length > 2) {
            if (data.getChannel() != null && Configs.getProperty("quoteChannels").stream().anyMatch(config -> config.getValue().equalsIgnoreCase(data.getChannel()))) {
                helen.sendOutgoingMessage(data.getResponseTarget(), data.getSender() + ": " + Quotes.deleteQuote(tokens[1], Integer.parseInt(tokens[2]), data.getChannel()));
            }
        } else {
            helen.sendOutgoingMessage(data.getResponseTarget(), data.getSender() + ": Please specify a username and index.  E.g. .rq DrMagnus 1");
        }
    }

    @IRCCommand(command = ".updateBans", startOfLine = true, securityLevel = 2, coexistWithJarvis = true)
    public void updateBans(CommandData data) {
       /* try {
            Bans.updateBans();
            helen.sendOutgoingMessage(data.getResponseTarget(), "Ban List successfully updated.");
            if (!Bans.getProblematicEntries().isEmpty()) {
                helen.sendOutgoingMessage(data.getResponseTarget(), "There are problematic entries for the following: " + Bans.getProblematicEntries().toString());
            }
        } catch (Exception e) {
            helen.sendOutgoingMessage(data.getChannel(), "Error parsing chat ban page. Please check the page for correct syntax.");
            logger.error("Exception attempting to update bans.", e);
        }*/
       helen.sendOutgoingMessage(data.getResponseTarget(), data.getSender() + ": I'm sorry this command is deprecated.  Please see my developer for more information.");
    }

    @IRCCommand(command = ".user", startOfLine = true, securityLevel = 1)
    public void getUserName(CommandData data) {
        String[] words = data.getSplitMessage();
        List<String> list = new ArrayList<>(Arrays.asList(words).subList(1, words.length));
        helen.sendOutgoingMessage(data.getResponseTarget(), data.getSender() + ": http://www.wikidot.com/user:info/" + StringUtils.join(list, "_"));
    }

    @IRCCommand(command = ".contest", startOfLine = true, securityLevel = 1)
    public void getContestInformation(CommandData data) {
        Optional<Config> property = Configs.getSingleProperty("contests");
        if (!property.isPresent()) {
            helen.sendOutgoingMessage(data.getResponseTarget(), data.getSender() + ": There is no contest currently running.");
        } else {
            helen.sendOutgoingMessage(data.getResponseTarget(), data.getSender() + ": " + property.get().getValue());
        }
    }

    @IRCCommand(command = ".blackbox", startOfLine = true, securityLevel = 1)
    public void getBlackbox(CommandData data) {
        helen.sendOutgoingMessage(data.getResponseTarget(), data.getSender() + ": " + "█");
    }

    private String buildResponse(List<Config> dbo) {
        StringBuilder str = new StringBuilder();
        str.append("{");
        for (int i = 0; i < dbo.size(); i++) {
            if (dbo.get(i).displayToUser()) {
                if (i != 0) {
                    str.append(dbo.get(i).getDelimiter());
                }
                str.append(dbo.get(i).toString());
            }
        }
        str.append("}");
        return str.toString();
    }
}
