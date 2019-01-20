package com.helen.commands;

import com.helen.bots.HelenBot;
import com.helen.database.*;
import com.helen.search.*;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.jibble.pircbot.User;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Command {
	private static final Logger logger = Logger.getLogger(Command.class);
	public static final String NOT_FOUND = "I'm sorry, I couldn't find anything.";
	public static final String ERROR = "I'm sorry, there was an error. Please inform DrMagnus.";

	private HelenBot helen;

	private boolean adminMode = false;
	private final int adminSecurity = 2;
	private int bullets = 6;

	private static HashMap<String, Method> hashableCommandList = new HashMap<String, Method>();
	private static HashMap<String, Method> slowCommands = new HashMap<String, Method>();
	private static HashMap<String, Method> regexCommands = new HashMap<String, Method>();

	public Command() {

	}

	public Command(HelenBot ircBot) {
		helen = ircBot;
	}

	static {
		logger.info("Initializing commandList.");
		for (Method m : Command.class.getDeclaredMethods()) {
			if (m.isAnnotationPresent(IRCCommand.class)) {
				if (m.getAnnotation(IRCCommand.class).startOfLine() && !m.getAnnotation(IRCCommand.class).reg()) {
					for (String s : ((IRCCommand) m.getAnnotation(IRCCommand.class)).command()) {
						hashableCommandList.put(s.toLowerCase(), m);
					}
				} else if (!m.getAnnotation(IRCCommand.class).reg()) {
					for (String s : ((IRCCommand) m.getAnnotation(IRCCommand.class)).command()) {
						slowCommands.put(s.toLowerCase(), m);
					}
				} else {
					for (String s : ((IRCCommand) m.getAnnotation(IRCCommand.class)).regex()) {
						regexCommands.put(s, m);
					}
				}
				for(String s: ((IRCCommand) m.getAnnotation(IRCCommand.class)).command()){
					logger.info("Loaded command: " + m + " with activation string " + s);
				}
			}
		}
		logger.info("Finished Initializing commandList.");
	}

	private void checkTells(CommandData data) {
		ArrayList<Tell> tells = Tells.getTells(data.getSender());

		if (tells.size() > 0) {
			helen.sendNotice(data.getSender(), "You have " + tells.size() + " pending tell(s).");
		}
		for (Tell tell : tells) {
			Tells.clearTells(tell.getNickGroupId() != null ? tell.getNickGroupId().toString() : tell.getTarget());
			helen.sendMessage(tell.getTarget(), tell.toString());

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
				if(user.isOp()){
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
			list = helen.getUsers(data.getChannel());
		}
		return list;
	}



	public void dispatchTable(CommandData data) {

		checkTells(data);
		User[] userList = getUserlist(data);
		Integer securityLevel = getSecurityLevel(userList, data);
		//logger.info("Entering dispatch table with command: \"" + data.getCommand() + "\"");

		// If we can use hashcommands, do so
		if (hashableCommandList.containsKey(data.getCommand().toLowerCase())) {
			try {

				Method m = hashableCommandList.get(data.getCommand().toLowerCase());
				if (m.getAnnotation(IRCCommand.class).coexistWithJarvis() || !helen.jarvisIsPresent(data.getChannel().toLowerCase())) {
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
						if (m.getAnnotation(IRCCommand.class).coexistWithJarvis() || !helen.jarvisIsPresent(data.getChannel())) {
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

							if(m.getAnnotation(IRCCommand.class).matcherGroup() != -1){
								data.setRegexTarget(match.group(m.getAnnotation(IRCCommand.class).matcherGroup()));
							}
							if (m.getAnnotation(IRCCommand.class).coexistWithJarvis() || !helen.jarvisIsPresent(data.getChannel())) {
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
						} catch (Exception e) {
							logger.error("Exception invoking command: "
									+ regexCommands.get(regex).getAnnotation(IRCCommand.class).command(), e);
						}
					}

			}
		}
	}

	// Relatively unregulated commands (anyone can try these)
	@IRCCommand(command = { ".HelenBot" }, startOfLine = true, coexistWithJarvis = true, securityLevel = 1)
	public void versionResponse(CommandData data) {
		if (data.getChannel().isEmpty()) {
			helen.sendMessage(data.getResponseTarget(), data.getSender() + ": Greetings, I am HelenBot v"
					+ Configs.getSingleProperty("version").getValue());
		}
		helen.sendMessage(data.getResponseTarget(),
				data.getSender() + ": Greetings, I am HelenBot v" + Configs.getSingleProperty("version").getValue());
	}

	@IRCCommand(command = { ".modeToggle" }, startOfLine = true, coexistWithJarvis = true, securityLevel = 3)
	public void toggleMode(CommandData data) {
		adminMode = !adminMode;
		helen.sendMessage(data.getResponseTarget(), data.getSender() + ": " + " I am now in " + (adminMode ? "Admin Only" : "Any User") + " mode.");
	}

	@IRCCommand(command = { ".checkJarvis" }, startOfLine = true, coexistWithJarvis = true, securityLevel = 2)
	public void findJarvisInChannel(CommandData data) {
		helen.jarvisReset(data.getChannel());
		helen.sendWho(data.getChannel());
		helen.sendMessage(data.getResponseTarget(), data.getSender() + ": Checking channel members...");
	}

	@IRCCommand(command = { ".jarvistest" }, startOfLine = true, coexistWithJarvis = true, securityLevel = 4)
	public void listTest(CommandData data) {
		helen.sendMessage(data.getResponseTarget(), data.getSender() + ": " + helen.jarvisIsPresent(data.getTarget()));
	}

	@IRCCommand(command = { ".ch", ".choose" }, startOfLine = true, securityLevel = 1)
	public void choose(CommandData data) {
		String[] choices = data.getMessage().substring(data.getMessage().indexOf(" ")).split(",");
		helen.sendMessage(data.getResponseTarget(),
				data.getSender() + ": " + choices[new Random().nextInt(choices.length)]);
	}

	@IRCCommand(command = { ".mode" }, startOfLine = true, coexistWithJarvis = true, securityLevel = 2)
	public void displayMode(CommandData data) {
		helen.sendMessage(data.getResponseTarget(),
				data.getSender() + ": I am currently in " + (adminMode ? "Admin" : "Any User") + " mode.");
	}

	@IRCCommand(command = { ".msg" }, startOfLine = true, securityLevel = 1)
	public void sendMessage(CommandData data) {
		String target = data.getTarget();
		String payload = data.getPayload();
		helen.sendMessage(target, data.getSender() + " said:" + payload);
	}

	@IRCCommand(command = {".addNick"}, startOfLine = true, coexistWithJarvis = true, securityLevel = 1)
	public void addNick(CommandData data){
		helen.sendMessage(data.getResponseTarget(), data.getSender() + ": " + Nicks.addNick(data));
	}

	@IRCCommand(command = {".deleteNick"}, startOfLine = true, coexistWithJarvis = true, securityLevel = 1)
	public void deleteNick(CommandData data){
		helen.sendMessage(data.getResponseTarget(), data.getSender() + ": " + Nicks.deleteNick(data));
	}

	@IRCCommand(command = {".deleteAllNicks"}, startOfLine = true, coexistWithJarvis = true, securityLevel = 1)
	public void deleteAllNicks(CommandData data){
		helen.sendMessage(data.getResponseTarget(), data.getSender() + ": " + Nicks.deleteAllNicks(data, true));
	}

	@IRCCommand(command = {".deleteNicksAdmin"}, startOfLine = true, coexistWithJarvis = true, securityLevel = 4)
	public void deleteNicksAdmin(CommandData data){

		helen.sendMessage(data.getResponseTarget(), data.getSender() + ": " + Nicks.deleteAllNicks(data, false));
	}

	@IRCCommand(command = { ".roll" }, startOfLine = true, securityLevel = 1)
	public void roll(CommandData data) {
		try {
			String result = Rolls.roll(data.getMessage(), data.getSender());
			helen.sendMessage(data.getResponseTarget(), data.getSender() + ": " + result);
		}
		catch (IncorrectUsageException e) {
			helen.sendMessage(data.getResponseTarget(), data.getSender() + ": " + e.getMessage());
		}
	}

	@IRCCommand(command = { ".myRolls", ".myrolls" }, startOfLine = true, securityLevel = 1)
	public void getRolls(CommandData data) {
		ArrayList<Roll> rolls = Rolls.getRolls(data.getSender());
		if (rolls.size() > 0) {
			helen.sendMessage(data.getResponseTarget(), buildResponse(rolls));
		} else {
			helen.sendMessage(data.getResponseTarget(),
					data.getSender() + ": *Checks her clipboard* Apologies, I do not have any saved rolls for you at this time.");
		}

	}

	@IRCCommand(command = {".hugme"}, startOfLine = true, coexistWithJarvis = true, securityLevel = 1)
	public void hugMe(CommandData data){
		if(data.isHugList()){
			helen.sendMessage(data.getResponseTarget(), data.getSender() + ": " + Hugs.storeHugmessage(data));
		}else{
			helen.sendMessage(data.getResponseTarget(), data.getSender() + ": You're not authorized to do that.");
		}
	}

	@IRCCommand(command={".hugHelen",".helenhug",".hugsplox"}, startOfLine = true, coexistWithJarvis = true, securityLevel = 1)
	public void hug(CommandData data){
		if(data.isHugList()){
			helen.sendMessage(data.getResponseTarget(), data.getSender() + ": " + Hugs.getHugMessage(data.getSender().toLowerCase()));
		}else if(data.isWhiteList()){
			helen.sendAction(data.getResponseTarget(), "hugs " + data.getSender() +".");
		}else{
			String[] messages = new String[]{
					"Thank you for the display of affection.",
					"*Click* Please remove your hands from my cylinders.",
					"Hugging a revolver must be difficult.",
					"You're smudging my finish.",
					"*Sigh* And I just calibrated my sights...",
					"I'm not sure why you're hugging me but...thank...you?",
					"Yes...Human emotion.  This is...nice.  Please let go of me."};

			helen.sendMessage(data.getResponseTarget(), data.getSender() + ": " + messages[((int) (Math.random() * (messages.length - 1)))]);
		}
	}

	@IRCCommand(command = { ".average", ".avg" }, startOfLine = true, securityLevel = 1)
	public void getAverage(CommandData data) {
		String average = Rolls.getAverage(data.getSplitMessage()[1], data.getSender());
		if (average != null) {
			helen.sendMessage(data.getResponseTarget(), data.getSender() + ": " + average);
		}

	}

	@IRCCommand(command = { ".g", ".google" }, startOfLine = true, securityLevel = 1)
	public void webSearch(CommandData data) {
		try {
			GoogleResults results = WebSearch.search(data.getMessage());
			helen.sendMessage(data.getResponseTarget(),
					data.getSender() + ": " + (results == null ? NOT_FOUND : results)
			);
		} catch (IOException e) {
			logger.error("Exception during web search", e);
		}
	}

	@IRCCommand(command = { ".gis" }, startOfLine = true, securityLevel = 1)
	public void imageSearch(CommandData data) {
		try {
			GoogleResults results = WebSearch.imageSearch(data.getMessage());
			helen.sendMessage(data.getResponseTarget(),
					data.getSender() + ": " + (results == null ? NOT_FOUND : results)
			);
		} catch (IOException e) {
			logger.error("Exception during image search", e);
		}
	}

	@IRCCommand(command = { ".w", ".wiki", ".wikipedia" }, startOfLine = true, securityLevel = 1)
	public void wikipediaSearch(CommandData data) {
		try {
			helen.sendMessage(data.getResponseTarget(),
					data.getSender() + ": " + WikipediaSearch.search(data, data.getMessage()));
		} catch (IOException e) {
			logger.error("Exception during Wikipedia search", e);
		}
	}

	@IRCCommand(command = { ".y", ".yt", ".youtube" }, startOfLine = true, securityLevel = 1)
	public void youtubeSearch(CommandData data) {
		helen.sendMessage(data.getResponseTarget(),
				data.getSender() + ": " + YouTubeSearch.youtubeSearch(data.getMessage()).toString());

	}

	@IRCCommand(command = {".helen",".helenHelp"}, startOfLine = true, securityLevel = 1, coexistWithJarvis = true)
	public void helenHelp(CommandData data){
		help(data);
	}

	@IRCCommand(command = ".help", startOfLine = true, securityLevel = 1)
	public void help(CommandData data){
		helen.sendMessage(data.getResponseTarget(), data.getSender() + ": You can find a list of my job responsibilities here:  http://home.helenbot.com/usage.html");
	}

	@IRCCommand(command = ".seen", startOfLine = true, securityLevel = 1)
	public void seen(CommandData data) {
		helen.sendMessage(data.getResponseTarget(), Users.seen(data));
	}

	@IRCCommand(command = ".sm", startOfLine = true, securityLevel = 1)
	public void selectResult(CommandData data){
		helen.sendMessage(data.getResponseTarget(), data.getSender() + ": " + Pages.getStoredInfo(data.getTarget(), data.getSender()));
	}

	@IRCCommand(command = {".lc",".l"}, startOfLine = true, securityLevel = 1)
	public void lastCreated(CommandData data){
		ArrayList<String> pages = Pages.lastCreated();
		ArrayList<String> infoz = new ArrayList<String>();
		if (pages != null) {
			for (String str : pages) {
				infoz.add(Pages.getPageInfo(str,data));
			}
			for(String str: infoz){
				helen.sendMessage(data.getResponseTarget(), data.getSender()
						+ ": " + str);
			}

		}else{
			helen.sendMessage(data.getResponseTarget(), data.getSender() + ": I can't do that yet.");
		}
	}

    @IRCCommand(command = {".hlc", ".hl"}, coexistWithJarvis = true, startOfLine = true, securityLevel = 1)
    public void lastCreatedHelen(CommandData data) {
        ArrayList<String> pages = Pages.lastCreated();
        ArrayList<String> infoz = new ArrayList<String>();
        if (pages != null) {
            for (String str : pages) {
                infoz.add(Pages.getPageInfo(str, data));
            }
            for (String str : infoz) {
                helen.sendMessage(data.getResponseTarget(), data.getSender()
                        + ": " + str);
            }

        } else {
            helen.sendMessage(data.getResponseTarget(), data.getSender() + ": I can't do that yet.");
        }
    }

	@IRCCommand(command = ".au", startOfLine = true, securityLevel = 1)
	public void authorDetail(CommandData data){
		helen.sendMessage(data.getResponseTarget(), data.getSender() + ": " + Pages.getAuthorDetail(
				data, data.getSplitMessage().length == 1 ? data.getSender() :  data.getMessageWithoutCommand()));
	}

	@IRCCommand(command = "SCPPAGEREGEX", startOfLine= true, reg = true, regex = { "http:\\/\\/www.scp-wiki.net\\/(.*)" }, securityLevel = 1, matcherGroup = 1)
	public void getPageInfo(CommandData data){
		if(!data.getRegexTarget().contains("/") && !data.getRegexTarget().contains("forum")){
			helen.sendMessage(data.getResponseTarget(), data.getSender() + ": " + Pages.getPageInfo(data.getRegexTarget()));
		}
	}

	@IRCCommand(command = "SCP", startOfLine = true, reg = true, regex = { "(scp|SCP)-([0-9]+)(-(ex|EX|j|J|arc|ARC))?" }, securityLevel = 1)
	public void scpSearch(CommandData data) {
		helen.sendMessage(data.getResponseTarget(), data.getSender() + ": " + Pages.getPageInfo(data.getCommand()));
	}

    @IRCCommand(command = {".s",".sea"}, startOfLine = true, securityLevel = 1)
	public void findSkip(CommandData data){
		helen.sendMessage(data.getResponseTarget(), data.getSender() + ": " + Pages.getPotentialTargets(data.getSplitMessage(), data.getSender()));
	}

	Pattern p = Pattern.compile("(scp|SCP)-([0-9]+)(-(ex|EX|j|J|arc|ARC))?");
    @IRCCommand(command = {".hs", ".hsea"}, coexistWithJarvis = true, startOfLine = true, securityLevel = 1)
    public void findSkipHelen(CommandData data) {
	    if(p.matcher(data.getTarget()).matches()){
            helen.sendMessage(data.getResponseTarget(), data.getSender() + ": " + Pages.getPageInfo(data.getTarget()));
        }else {
            helen.sendMessage(data.getResponseTarget(), data.getSender() + ": " + Pages.getPotentialTargets(data.getSplitMessage(), data.getSender()));
        }
    }

	@IRCCommand(command = {".pronouns", ".pronoun"}, startOfLine = true, coexistWithJarvis = true, securityLevel = 1)
	public void getPronouns(CommandData data) {

		helen.sendMessage(data.getResponseTarget(), data.getSender() + ": " +
				Pronouns.getPronouns((data.getSplitMessage().length > 1) ? data.getTarget() : data.getSender()));


	}

	@IRCCommand(command = ".myPronouns", startOfLine = true, coexistWithJarvis = true, securityLevel = 1)
	public void myPronouns(CommandData data) {
		helen.sendMessage(data.getResponseTarget(), data.getSender() + ": " + Pronouns.getPronouns(data.getSender()));
	}
	//TODO make this less stupid
	@IRCCommand(command = ".helenconf", startOfLine = true, coexistWithJarvis = true, securityLevel = 4)
	public void configure(CommandData data) {
		if(data.getSplitMessage().length == 1){
			helen.sendMessage(data.getResponseTarget(), data.getSender() + ": " + "{shoot|lcratings}");
		}else{
			helen.sendMessage(data.getResponseTarget(), data.getSender() + ": " + Configs.insertToggle(data, data.getTarget(),
					data.getSplitMessage()[2].equalsIgnoreCase("true") ? true : false));
		}
	}

	@IRCCommand(command = ".setPronouns", startOfLine = true, coexistWithJarvis = true, securityLevel = 1)
	public void setPronouns(CommandData data) {
		String response = Pronouns.insertPronouns(data);
		if (response.contains("banned term")) {
			for(Config c : Configs.getProperty("registeredNicks")){
				Tells.sendTell(c.getValue(), "Secretary_Helen",
						"User " + data.getSender() + " attempted to add a banned term:" + response +". Their full message "
						+ "was: " + data.getMessage(), true);

			}
		}
		helen.sendMessage(data.getResponseTarget(), data.getSender() + ": " + response);
	}

	@IRCCommand(command = ".clearPronouns", startOfLine = true, coexistWithJarvis = true, securityLevel = 1)
	public void clearPronouns(CommandData data) {
		helen.sendMessage(data.getResponseTarget(), data.getSender() + ": " + Pronouns.clearPronouns(data.getSender()));
	}

	@IRCCommand(command = ".deletePronouns", startOfLine = true, coexistWithJarvis = true, securityLevel = 2)
	public void removePronouns(CommandData data) {
		if(data.getTarget() != null){
			helen.sendMessage(data.getResponseTarget(), data.getSender() + ": " + Pronouns.clearPronouns(data.getTarget()));
		}
		else{
			helen.sendMessage(data.getResponseTarget(), data.getSender() + ": Please specify the user to delete pronouns for.");
		}

	}

	@IRCCommand(command = {".def",".definition"}, startOfLine = true, coexistWithJarvis = false, securityLevel = 1)
	public void define(CommandData data){
		helen.sendMessage(data.getResponseTarget(), data.getSender() + ": " + WebsterSearch.dictionarySearch(data.getTarget()));
	}

	// Authentication Required Commands
	@IRCCommand(command = ".join", startOfLine = true, coexistWithJarvis = true, securityLevel = 3)
	public void enterChannel(CommandData data) {
		helen.joinJarvyChannel(data.getTarget());

	}

	@IRCCommand(command = ".leave", startOfLine = true, coexistWithJarvis = true, securityLevel = 3)
	public void leaveChannel(CommandData data) {
		helen.partChannel(data.getTarget());
	}

	@IRCCommand(command = ".tell", startOfLine = true, securityLevel = 1)
	public void tell(CommandData data) {
		String str = Tells.sendTell(data.getTarget(), data.getSender(), data.getTellMessage(),
				(data.getChannel().isEmpty() ? true : false));
		helen.sendMessage(data.getResponseTarget(), data.getSender() + ": " + str);
	}

	@IRCCommand(command = ".mtell", startOfLine = true, securityLevel = 1,coexistWithJarvis = true)
	public void multiTell(CommandData data) {
		String str = Tells.sendMultitell(data);
		helen.sendMessage(data.getResponseTarget(), data.getSender() + ": " + str);
	}

	@IRCCommand(command = ".exit", startOfLine = true, coexistWithJarvis = true, securityLevel = 4)
	public void exitBot(CommandData data) {
		for (String channel : helen.getChannels()) {
			helen.partChannel(channel, "Stay out of the revolver's sights...");
		}
		try {
			Thread.sleep(5000);
		} catch (Exception e) {

		}
		helen.disconnect();
		System.exit(0);

	}

	@IRCCommand(command = ".allProperties", startOfLine = true, securityLevel = 3)
	public void getAllProperties(CommandData data) {
		ArrayList<Config> properties = Configs.getConfiguredProperties(true);
		helen.sendMessage(data.getResponseTarget(),
				data.getSender() + ": Configured properties: " + buildResponse(properties));
	}

	@IRCCommand(command = ".property", startOfLine = true, coexistWithJarvis = true, securityLevel = 2)
	public void getProperty(CommandData data) {
		ArrayList<Config> properties = Configs.getProperty(data.getTarget());
		helen.sendMessage(data.getResponseTarget(),
				data.getSender() + ": Configured properties: " + buildResponse(properties));
	}

	@IRCCommand(command = ".setProperty", startOfLine = true, coexistWithJarvis = true, securityLevel = 4)
	public void setProperty(CommandData data) {
		String properties = Configs.setProperty(data.getSplitMessage()[1], data.getSplitMessage()[2],
				data.getSplitMessage()[3]);
		helen.sendMessage(data.getResponseTarget(), data.getSender() + ": " + properties);
	}

	@IRCCommand(command = ".updateProperty", startOfLine = true, coexistWithJarvis = true, securityLevel = 4)
	public void updateProperty(CommandData data) {
		String properties = Configs.updateSingle(data.getSplitMessage()[1], data.getSplitMessage()[2],
				data.getSplitMessage()[3]);
		helen.sendMessage(data.getResponseTarget(), data.getSender() + ": " + properties);
	}

	@IRCCommand(command = ".deleteProperty", startOfLine = true, securityLevel = 4)
	public void deleteProperty(CommandData data) {
		String properties = Configs.removeProperty(data.getSplitMessage()[1], data.getSplitMessage()[2]);
		helen.sendMessage(data.getResponseTarget(), data.getSender() + ": " + properties);
	}

	@IRCCommand(command = {".clearCache",".clear"}, startOfLine = true, securityLevel = 4)
	public void clearCache(CommandData data) {
		Queries.clear();
		Configs.clear();
		Pronouns.reload();
	}

	@IRCCommand(command = ".shoot", startOfLine = true, securityLevel = 4, coexistWithJarvis = true)
	public void shootUser(CommandData data) {
		if(Configs.commandEnabled(data, "shoot")){
			if(data.getTarget().equalsIgnoreCase("Secretary_Helen")){
				bullets--;
				helen.sendAction(data.getChannel(), "shoots " + data.getSender());
				if(bullets < 1){
					reload(data);
				}
			}else{
				helen.sendAction(data.getChannel(), "shoots " + data.getTarget());
				bullets--;
				if(bullets < 1){
					reload(data);
				}
				helen.sendMessage(data.getChannel(), "Be careful " + data.getTarget() + ". I still have " +
				(bullets > 1 ? bullets + " bullets left." : "one in the chamber."));
			}
		}
	}

	@IRCCommand(command = ".reload", startOfLine = true, securityLevel = 4)
	public void reload(CommandData data) {
		helen.sendAction(data.getChannel(), "reloads all six cylinders.");
		bullets = 6;
	}

	@IRCCommand(command = ".unload", startOfLine = true, securityLevel = 4, coexistWithJarvis = true)
	public void unload(CommandData data) {
		if(Configs.commandEnabled(data, "shoot")){
			if(data.getTarget().equalsIgnoreCase("Secretary_Helen")){
				bullets--;
				helen.sendAction(data.getChannel(), "shoots " + data.getSender());
				if(bullets < 1){
					reload(data);
				}
			}else{
				helen.sendAction(data.getChannel(), "calmly thumbs back the hammer and unleashes"
						+ (bullets == 6 ? " all six cylinders on " : " the remaining " + bullets + " cylinders on ")
				+ data.getTarget() + ".");
				helen.sendMessage(data.getChannel(), "Stay out of the revolver's sights.");
				reload(data);
			}
		}
	}

	@IRCCommand(command = ".discord", startOfLine = true, securityLevel = 4, coexistWithJarvis = true)
	public void showDiscordMessage(CommandData data){
		helen.sendMessage(data.getChannel(), "There are currently no plans for an official SCP Discord." +
		" Staff feel that, at this time, the benefits of Discord do not outweigh the difficulties of moderation," +
				" and the resulting fracturing between IRC and Discord. There are also several concerns about " +
				"the technical and financial viability of discord.");
	}

	@IRCCommand(command = ".updateBans", startOfLine = true, securityLevel = 4, coexistWithJarvis = true)
	public void updateBans(CommandData data) {
		try {
			Bans.updateBans();
			helen.sendMessage(data.getChannel(), "Ban List successfully updated.");
		} catch (Exception e) {
			helen.sendMessage(data.getChannel(), "Error parsing chat ban page. Please check the page for correct syntax.");
			logger.error("Exception attempting to update bans.",e);
		}
	}

	@IRCCommand(command = ".user", startOfLine = true, securityLevel = 1, coexistWithJarvis = false)
	public void getUserName(CommandData data){
    	List<String> list = new ArrayList<>();
    	String[] words = data.getSplitMessage();
    	for(int i = 1; i < words.length; i++){
    		list.add(words[i]);
		}
    	helen.sendMessage(data.getChannel(), data.getSender() + ": http://www.wikidot.com/user:info/" + StringUtils.join(list,"_"));
	}

	private String buildResponse(ArrayList<? extends DatabaseObject> dbo) {
		StringBuilder str = new StringBuilder();
		str.append("{");
		for (int i = 0; i < dbo.size(); i++) {
			if(dbo.get(i).displayToUser()){
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
