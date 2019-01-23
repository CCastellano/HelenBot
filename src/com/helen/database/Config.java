package com.helen.database;

public class Config implements DatabaseObject {
	
	private String key = null;
	private String value = null;
	private String lastUpdated = null;
	private boolean displayToPublic = false;
	
	
	public Config(String key, String value, String lastUpdated, boolean displayToPublic){
		this.key = key;
		this.value = value;
		this.lastUpdated = lastUpdated;
		this.displayToPublic = displayToPublic;
	}
	
	public boolean isPublic(){
		return displayToPublic;
	}
	
	public String getKey(){
		return key;
	}
	
	public String getValue(){
		return value;
	}
	
	public String getUpdated(){
		return lastUpdated;
	}
	
	public String getDelimiter(){
		return Configs.getSingleProperty("configDelim").getValue();
	}
	
	public String toString(){
		return key + ":" + value;
	}
	
	public boolean displayToUser(){
		return displayToPublic;
	}

}
