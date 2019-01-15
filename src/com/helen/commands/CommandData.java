package com.helen.commands;

import com.helen.database.Config;
import com.helen.database.Configs;

public class CommandData {
	private String channel;
	private String sender;
	private String login;
	private String hostname;
	private String message;
	private String regexTarget;

	
	public CommandData(String channel, String sender, String login, String hostname, String message) {
		this.channel = channel;
		this.sender = sender;
		this.login = login;
		this.hostname = hostname;
		this.message = message.trim();
	}
	
	

	public boolean isPrivate(){
		return getChannel() == null || getChannel().isEmpty();
	}
	
	public String getRegexTarget(){
		return regexTarget;
	}
	
	public void setRegexTarget(String str){
		regexTarget = str;
	}
	
	public String getResponseTarget(){
		return (isPrivate()) ? getSender() : getChannel();
	}

	public String getChannel() {
		return channel;
	}
	
	public String[] getSplitMessage(){
		return getMessage().split(" ");
	}


	public String getSender() {
		return sender;
	}


	public String getLogin() {
		return login;
	}


	public String getHostname() {
		return hostname;
	}


	public String getMessage() {
		return message;
	}
	
	public String getCommand() {
		return message.split(" ")[0];
	}
	
	public String getTarget() {
		return message.split(" ")[1];
	}
	
	public String getMessageWithoutCommand() {
		return message.substring((message.split(" ")[0].length() + 1),message.length());
	}
	
	public String getTellMessage() {
		return message.substring((message.split(" ")[0].length() + message.split(" ")[1].length() + 2),message.length());
	}
	
	public String getPayload() {
		return message.substring(message.indexOf(getTarget()) + getTarget().length()).trim();
	}
	
	public boolean isWhiteList(){
		for(Config config : Configs.getProperty("registeredNicks")){
			if(getSender().equals(config.getValue())){
				return true;
			}
		}
		return false;
	}
	
	public boolean isHugList(){
		for(Config config : Configs.getProperty("hugs")){
			if(getSender().equalsIgnoreCase(config.getValue())){
				return true;
			}
		}
		return false;
	}
	
}
