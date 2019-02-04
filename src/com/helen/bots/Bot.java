package com.helen.bots;

import com.helen.commands.*;
import org.jibble.pircbot.IrcException;
import org.jibble.pircbot.User;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.UnsupportedEncodingException;

/** PircBot functionality as an interface.
 * This allows {@link OfflineBot} and the test suite's TestBot
 * to be substituted in for {@link HelenBot}.
 */
@SuppressWarnings({"unused", "RedundantThrows"})
public interface Bot {
  // Helen methods.
  default boolean jarvisIsPresent(@Nullable String channel){ return false; }
  default void jarvisReset(String channel){}
  default void joinJarvyChannel(String channel){}

  default void sendWho(String channel){}

  default void sendReply(CommandData data, @Nullable String reply) {
    sendMessage(data.getResponseTarget(),
        data.sender + ": " + (reply == null ? Command.NOT_FOUND : reply)
    );
  }

  // PircBot methods.
  String[] getChannels();
  User[] getUsers(String channel);
  /*
  String getName();
  String getNick();
  String getLogin();
  String getVersion();
  String getFinger();
  long getMessageDelay();
  int getMaxLineLength();
  int getOutgoingQueueSize();
  String getServer();
  int getPort();
  String getPassword();
  int[] longToIp(long address);
  long ipToLong(byte[] address);
  String getEncoding();
  InetAddress getInetAddress();
  */

  default boolean isConnected(){ return true; }
  default void setAutoNickChange(boolean autoNickChange){}
  default void startIdentServer(){}
  default void joinChannel(String channel){}
  default void joinChannel(String channel, String key){}
  default void partChannel(String channel){}
  default void partChannel(String channel, String reason){}
  default void quitServer(){}
  default void quitServer(String reason){}
  default void sendMessage(String target, String message){}
  default void sendAction(String target, String action){}
  default void sendNotice(String target, String notice){}
  default void sendCTCPCommand(String target, String command){}
  default void changeNick(String newNick){}
  default void identify(String password){}
  default void setMode(String channel, String mode){}
  default void sendInvite(String nick, String channel){}
  default void ban(String channel, String hostmask){}
  default void unBan(String channel, String hostmask){}
  default void op(String channel, String nick){}
  default void deOp(String channel, String nick){}
  default void voice(String channel, String nick){}
  default void deVoice(String channel, String nick){}
  default void setTopic(String channel, String topic){}
  default void kick(String channel, String nick){}
  default void kick(String channel, String nick, String reason){}
  default void listChannels(){}
  default void listChannels(String parameters){}
  default void log(String line){}
  default void setVerbose(boolean verbose){}
  default void setMessageDelay(long delay){}
  default void setEncoding(String charset)
      throws UnsupportedEncodingException {}

  default void connect(String hostname)
      throws IOException, IrcException {}
  default void connect(String hostname, int port)
      throws IOException, IrcException {}
  default void connect(String hostname, int port, String password)
      throws IOException, IrcException {}
  default void reconnect()
      throws IOException, IrcException {}
  default void disconnect(){}
  default void sendRawLine(String line){}
  default void sendRawLineViaQueue(String line){}

  /* Protected methods
  default void handleLine(String line){}
  default void onConnect(){}
  default void onDisconnect(){}
  default void onServerResponse(int code, String response){}
  default void onUserList(String channel, User[] users){}
  default void onMessage(String channel, String sender, String login, String hostname, String message){}
  default void onPrivateMessage(String sender, String login, String hostname, String message){}
  default void onAction(String sender, String login, String hostname, String target, String action){}
  default void onNotice(String sourceNick, String sourceLogin, String sourceHostname, String target, String notice){}
  default void onJoin(String channel, String sender, String login, String hostname){}
  default void onPart(String channel, String sender, String login, String hostname){}
  default void onNickChange(String oldNick, String login, String hostname, String newNick){}
  default void onKick(String channel, String kickerNick, String kickerLogin, String kickerHostname, String recipientNick, String reason){}
  default void onQuit(String sourceNick, String sourceLogin, String sourceHostname, String reason){}
  default void onTopic(String channel, String topic){}
  default void onTopic(String channel, String topic, String setBy, long date, boolean changed){}
  default void onChannelInfo(String channel, int userCount, String topic){}
  default void onMode(String channel, String sourceNick, String sourceLogin, String sourceHostname, String mode){}
  default void onUserMode(String targetNick, String sourceNick, String sourceLogin, String sourceHostname, String mode){}
  default void onOp(String channel, String sourceNick, String sourceLogin, String sourceHostname, String recipient){}
  default void onDeop(String channel, String sourceNick, String sourceLogin, String sourceHostname, String recipient){}
  default void onVoice(String channel, String sourceNick, String sourceLogin, String sourceHostname, String recipient){}
  default void onDeVoice(String channel, String sourceNick, String sourceLogin, String sourceHostname, String recipient){}
  default void onSetChannelKey(String channel, String sourceNick, String sourceLogin, String sourceHostname, String key){}
  default void onRemoveChannelKey(String channel, String sourceNick, String sourceLogin, String sourceHostname, String key){}
  default void onSetChannelLimit(String channel, String sourceNick, String sourceLogin, String sourceHostname, int limit){}
  default void onRemoveChannelLimit(String channel, String sourceNick, String sourceLogin, String sourceHostname){}
  default void onSetChannelBan(String channel, String sourceNick, String sourceLogin, String sourceHostname, String hostmask){}
  default void onRemoveChannelBan(String channel, String sourceNick, String sourceLogin, String sourceHostname, String hostmask){}
  default void onSetTopicProtection(String channel, String sourceNick, String sourceLogin, String sourceHostname){}
  default void onRemoveTopicProtection(String channel, String sourceNick, String sourceLogin, String sourceHostname){}
  default void onSetNoExternalMessages(String channel, String sourceNick, String sourceLogin, String sourceHostname){}
  default void onRemoveNoExternalMessages(String channel, String sourceNick, String sourceLogin, String sourceHostname){}
  default void onSetInviteOnly(String channel, String sourceNick, String sourceLogin, String sourceHostname){}
  default void onRemoveInviteOnly(String channel, String sourceNick, String sourceLogin, String sourceHostname){}
  default void onSetModerated(String channel, String sourceNick, String sourceLogin, String sourceHostname){}
  default void onRemoveModerated(String channel, String sourceNick, String sourceLogin, String sourceHostname){}
  default void onSetPrivate(String channel, String sourceNick, String sourceLogin, String sourceHostname){}
  default void onRemovePrivate(String channel, String sourceNick, String sourceLogin, String sourceHostname){}
  default void onSetSecret(String channel, String sourceNick, String sourceLogin, String sourceHostname){}
  default void onRemoveSecret(String channel, String sourceNick, String sourceLogin, String sourceHostname){}
  default void onInvite(String targetNick, String sourceNick, String sourceLogin, String sourceHostname, String channel) {}
  default void onDccSendRequest(String sourceNick, String sourceLogin, String sourceHostname, String filename, long address, int port, int size){}
  default void onDccChatRequest(String sourceNick, String sourceLogin, String sourceHostname, long address, int port){}
  default void onVersion(String sourceNick, String sourceLogin, String sourceHostname, String target){}
  default void onPing(String sourceNick, String sourceLogin, String sourceHostname, String target, String pingValue){}
  default void onServerPing(String response){}
  default void onTime(String sourceNick, String sourceLogin, String sourceHostname, String target){}
  default void onFinger(String sourceNick, String sourceLogin, String sourceHostname, String target){}
  default void onUnknown(String line){}
  default void setName(String name){}
  default void setLogin(String login){}
  default void setVersion(String version){}
  default void setFinger(String finger){}
  */
}
