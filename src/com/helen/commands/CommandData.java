package com.helen.commands;

import com.helen.*;
import com.helen.database.*;

import javax.annotation.Nullable;

public class CommandData {
  @Nullable public final String channel;
  public final String sender;
  public final String login;
  public final String hostname;
  public final String message;
  public final String[] splitMessage;
  @Nullable public String regexTarget;

  public CommandData(@Nullable String channel, String sender, String login, String hostname, String message) {
    this.channel      = channel;
    this.sender       = sender;
    this.login        = login;
    this.hostname     = hostname;
    this.message      = message.trim();
    this.splitMessage = Utils.split(' ', this.message);
  }

  @Nullable
  private String subMsg(int beginIndex) {
    return beginIndex < message.length() ? message.substring(beginIndex).trim() : null;
  }

  public String getResponseTarget() {
    return channel == null ? sender : channel;
  }

  @Nullable
  public String getCommand() {
    return splitMessage.length > 0 ? splitMessage[0] : null;
  }

  @Nullable
  public String getTarget() {
    return splitMessage.length > 1 ? splitMessage[1] : null;
  }

  @Nullable
  public String getMessageWithoutCommand() {
    return splitMessage.length > 1 ? subMsg(splitMessage[0].length() + 1) : null;
  }

  @Nullable
  public String getTellMessage() {
    return splitMessage.length > 2
           ? subMsg(splitMessage[0].length() + splitMessage[1].length() + 2)
           : null;
  }

  @Nullable
  public String getPayload() {
    return splitMessage.length > 2
           ? subMsg(message.indexOf(splitMessage[1]) + splitMessage[1].length())
           : null;
  }

  public boolean isWhiteList() {
    return Configs
        .getProperty("registeredNicks")
        .stream()
        .anyMatch(config -> sender.equalsIgnoreCase(config.value));
  }

  public boolean isHugList() {
    return Configs
        .getProperty("hugs")
        .stream()
        .anyMatch(config -> sender.equalsIgnoreCase(config.value));
  }

}
