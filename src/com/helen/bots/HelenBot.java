package com.helen.bots;

import com.helen.commands.Command;
import com.helen.commands.CommandData;
import com.helen.database.*;
import org.apache.log4j.Logger;
import org.jibble.pircbot.IrcException;
import org.jibble.pircbot.NickAlreadyInUseException;
import org.jibble.pircbot.PircBot;

import java.io.IOException;
import java.util.HashMap;

public class HelenBot extends PircBot {

	private static Command cmd = null;
	
	private static final Logger logger = Logger.getLogger(HelenBot.class);
	private static HashMap<String, Boolean> jarvisPresent = new HashMap<String, Boolean>();

	public HelenBot() throws NickAlreadyInUseException, IOException,
			IrcException, InterruptedException {
		logger.info("Initializing HelenBot v"
				+ Configs.getSingleProperty("version").getValue());
		this.setVerbose(true);
		connect();
		joinChannels();
		Bans.updateBans();
		cmd = new Command(this);
	}

	private void connect() throws NickAlreadyInUseException, IOException,
			IrcException, InterruptedException {

		this.setLogin(Configs.getSingleProperty("hostname").getValue());
		this.setName(Configs.getSingleProperty("bot_name").getValue());
		try {
			this.connect(Configs.getSingleProperty("server").getValue());
		} catch (NickAlreadyInUseException e) {
			this.identify(Configs.getSingleProperty("pass").getValue());
		}
		Thread.sleep(1000l);
		this.identify(Configs.getSingleProperty("pass").getValue());
		Thread.sleep(2000l);
	}

	private void joinChannels() {

		for (Config channel : Configs.getProperty("autojoin")) {
			this.joinChannel(channel.getValue());
			sendWho(channel.getValue());
		}
	}

	public void joinJarvyChannel(String channel){
		this.joinChannel(channel);
		sendWho(channel);
	}

	public void onMessage(String channel, String sender, String login,
			String hostname, String message) {
		Users.insertUser(sender, hostname, message, channel.toLowerCase());
		cmd.dispatchTable(new CommandData(channel, sender, login, hostname,
				message));
	}

	public void onPrivateMessage(String sender, String login, String hostname,
			String message) {
		dispatchTable(sender, login, hostname, message);
	}

	private void dispatchTable(String sender, String login, String hostname,
			String message) {
		cmd.dispatchTable(new CommandData("", sender, login, hostname, message));
	}

	public void sendWho(String channel){
		this.sendRawLine("WHO " + channel);
	}


	public void log(String line) {
		if (!line.contains("PING :") && !line.contains(">>>PONG")) {
			logger.info(System.currentTimeMillis() + " " + line);
		}
	}

	public void onUserList(String channel, Users[] users) {
		logger.info("Recieved user list for a channel" + channel);
	}

	public void onServerResponse(int code, String response) {
		if (code == 352) {
			logger.info(response);
			if(response.split(" ")[5].equalsIgnoreCase("jarvis")){
				jarvisPresent.put(response.split(" ")[1].toLowerCase(), true);
			}
		}
	}

	public void jarvisReset(String channel){
		jarvisPresent.remove(channel.toLowerCase());
	}

	public void onDisconnect(){
		int tries = 0;
		while(!this.isConnected()){
			try{
				tries++;
				this.connect();
				if(this.isConnected()){
					this.joinChannels();
				}
			}catch(Exception e){
				logger.error(e);
				if(tries > 10){
					logger.error("Shutting down HelenBot!");
					System.exit(1);
				}
				try {
					Thread.sleep(10000);
				}catch(Exception ex){
					logger.error(ex);
				}
			}
		}
	}


	public void onPart(String channel, String sender, String login,
			String hostname) {
		if (sender.equalsIgnoreCase("jarvis")) {
			if (jarvisPresent.containsKey(channel.toLowerCase())) {
				jarvisPresent.remove(channel.toLowerCase());
			}
		}
	}
	
	public void onQuit(String sourceNick,
            String sourceLogin,
            String sourceHostname,
            String reason) {
		
		if (sourceNick.equalsIgnoreCase("jarvis")) {
			for(String channel : jarvisPresent.keySet()){
				jarvisPresent.put(channel, false);
			}
		}
	}

	public Boolean jarvisIsPresent(String channel) {
		return jarvisPresent.getOrDefault(channel.toLowerCase(), false);
	}

	public void onJoin(String channel, String sender, String login,
			String hostmask) {
		if (sender.equalsIgnoreCase("jarvis")) {
			jarvisPresent.put(channel.toLowerCase(), true);

		}
		//removing extraneous logging
		//logger.info("JOINED: " + sender + " LOGIN: " + login + " HOSTNAME: " + hostmask + " CHANNEL: " + channel);
		//Testing in separate channel
		try {
			BanInfo info = Bans.getUserBan(sender, hostmask, channel);
			if (info != null) {
				kick(channel, sender, info.getBanReason());
				ban(channel, hostmask);
			}
		}catch(Exception e){
			logger.error("Exception attempting onjoin for " + channel + " , " + sender + " " + login + " " + hostmask,e);
		}
	}

	public final void sendReply(CommandData data, String reply) {
		this.sendMessage(data.getResponseTarget(), data.getSender() + ": " + reply);
	}
}
