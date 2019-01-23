package com.helen.database;

import java.text.SimpleDateFormat;

public class Tell {

	private String sender;
	private String target;
	private java.sql.Timestamp tell_time;
	private String message;
	private boolean privateMessage = true;
	private Integer nickGroupId = null;
	
	
	public Tell(String sender, String target, java.sql.Timestamp tell_time, String message, boolean privateMessage){
		this.sender = sender;
		this.target = target;
		this.tell_time = tell_time;
		this.message = message;
		this.privateMessage = privateMessage;
	}

	public Tell(String sender, String target, java.sql.Timestamp tell_time, String message, boolean privateMessage, Integer id){
		this.sender = sender;
		this.target = target;
		this.tell_time = tell_time;
		this.message = message;
		this.privateMessage = privateMessage;
		this.nickGroupId = id;
	}
	
	public String toString(){
		return target + ": " + sender + " said at " +
				new SimpleDateFormat("MM/dd/yyyy HH:mm:ss").format(tell_time) + ": " + message;
	}

	public Integer getNickGroupId() {
		return nickGroupId;
	}
	
	public String getSender(){
		return sender;
	}

	public String getTarget() {
		return target;
	}

	public java.sql.Timestamp getTell_time() {
		return tell_time;
	}

	public String getMessage() {
		return message;
	}
	
	public boolean isPrivate(){
		return privateMessage;
	}
}
