package com.helen.bots;

import com.helen.commands.Command;
import com.helen.commands.CommandData;
import com.helen.database.framework.*;
import com.helen.database.users.BanInfo;
import com.helen.database.users.Bans;
import org.apache.log4j.Logger;
import org.jibble.pircbot.IrcException;
import org.jibble.pircbot.NickAlreadyInUseException;
import org.jibble.pircbot.PircBot;
import org.jibble.pircbot.User;

import java.io.IOException;
import java.util.Optional;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

public class HelenBot extends PircBot implements BotFramework{

    private static final Logger logger = Logger.getLogger(HelenBot.class);
    private static Command cmd = null;
    private static Timer timer = new Timer();

    public HelenBot() throws IOException,
            IrcException, InterruptedException {

        configure();
    }

    private void configure() throws IOException,
            IrcException, InterruptedException {
        Optional<Config> version = Configs.getSingleProperty("version");
        if (version.isPresent()) {
            logger.info("Initializing HelenBot v"
                    + version.get().getValue());
            this.setVerbose(true);
            connect();
            joinChannels();
            Bans.updateBans();
            cmd = new Command(this);
        } else {
            logger.error("Version info is missing, please check configs.");
            System.exit(1);
        }
    }

    private void connect() throws IOException,
            IrcException, InterruptedException {
        Optional<Config> loginName = Configs.getSingleProperty("hostname");
        Optional<Config> botName = Configs.getSingleProperty("bot_name");
        Optional<Config> server = Configs.getSingleProperty("server");
        Optional<Config> pass = Configs.getSingleProperty("pass");
        if (loginName.isPresent() && botName.isPresent() && server.isPresent() && pass.isPresent()) {
            this.setLogin(loginName.get().getValue());
            this.setName(botName.get().getValue());
            try {
                this.connect(server.get().getValue());
            } catch (NickAlreadyInUseException e) {
                this.identify(pass.get().getValue());
            }
            Thread.sleep(1000L);
            this.identify(pass.get().getValue());
            Thread.sleep(2000L);
            this.sendRawLine("MODE Secretary_Helen +B");
            Thread.sleep(2000L);
        }


    }

    private void joinChannels() {

        for (Config channel : Configs.getProperty("autojoin")) {
            Optional<Config> password = Configs.getSingleProperty(channel.getValue());
            if(password.isPresent()){
                this.joinChannel(channel.getValue(),password.get().getValue());
            }else {
                this.joinChannel(channel.getValue());
            }
        }
    }

    public void onKick(String channel,
                       String kickerNick,
                       String kickerLogin,
                       String kickerHostname,
                       String recipientNick,
                       String reason){
        Set<String> channels = Configs.getFastConfigs("logChannels");
        if(channels.contains(channel)){
            try(CloseableStatement stmt = Connector.getStatement(Queries.getQuery("logMessage"), recipientNick, channel, recipientNick + " was kicked by " + kickerNick + " for: " +reason)){
                if(stmt != null){
                    try {
                        stmt.executeUpdate();
                    }catch(Exception e){
                        logger.error("There wsa an error logging a line",e);
                    }
                }
            }
        }
    }

    public void onSetChannelBan(String channel,
                                String sourceNick,
                                String sourceLogin,
                                String sourceHostname,
                                String hostmask){
        Set<String> channels = Configs.getFastConfigs("logChannels");
        if(channels.contains(channel)){
            try(CloseableStatement stmt = Connector.getStatement(Queries.getQuery("logMessage"), sourceNick, channel, sourceNick + " has banned " + hostmask)){
                if(stmt != null){
                    try {
                        stmt.executeUpdate();
                    }catch(Exception e){
                        logger.error("There wsa an error logging a line",e);
                    }
                }
            }
        }
    }

    public void onRemoveChannelBan(String channel,
                                String sourceNick,
                                String sourceLogin,
                                String sourceHostname,
                                String hostmask){
        Set<String> channels = Configs.getFastConfigs("logChannels");
        if(channels.contains(channel)){
            try(CloseableStatement stmt = Connector.getStatement(Queries.getQuery("logMessage"), sourceNick, channel, sourceNick + " has unbanned " + hostmask)){
                if(stmt != null){
                    try {
                        stmt.executeUpdate();
                    }catch(Exception e){
                        logger.error("There wsa an error logging a line",e);
                    }
                }
            }
        }
    }

    public void onAction(String sender, String login, String hostname, String target, String action){
        Set<String> channels = Configs.getFastConfigs("logChannels");
        if(channels.contains(target)){
            try(CloseableStatement stmt = Connector.getStatement(Queries.getQuery("logMessage"), sender, target, action)){
                if(stmt != null){
                    try {
                        stmt.executeUpdate();
                    }catch(Exception e){
                        logger.error("There wsa an error logging a line",e);
                    }
                }
            }
        }
    }

    public void onMessage(String channel, String sender, String login,
                          String hostname, String message) {
        Set<String> channels = Configs.getFastConfigs("logChannels");
        if(channels.contains(channel)){
            try(CloseableStatement stmt = Connector.getStatement(Queries.getQuery("logMessage"), sender, channel, message)){
                if(stmt != null){
                    try {
                        stmt.executeUpdate();
                    }catch(Exception e){
                        logger.error("There wsa an error logging a line",e);
                    }
                }
            }
        }
        if(sender.equalsIgnoreCase("nickserv") || sender.equalsIgnoreCase("chanserv")){
            logger.info(sender + ": " + message);
        }
        if(!checkBan(channel,sender,login,hostname)){
            cmd.dispatchTable(new CommandData(channel, sender, login, hostname,message));
        }
    }

    public User[] getChannelUsers(String channel){
        return super.getUsers(channel);
    }

    public void kickUser(String channel, String sender, String message){
        super.kick(channel,sender,message);
    }

    @Override
    public String[] getConnectedChannels() {
        return super.getChannels();
    }

    @Override
    public void leaveChannel(String channel, String message) {
        super.partChannel(channel,message);
    }

    @Override
    public void disconnectFromServer() {
        super.disconnect();
    }

    @Override
    public void sendBotAction(String channel, String message) {
        super.sendAction(channel,message);
    }

    @Override
    public void joinAChannel(String channel) {
        super.joinChannel(channel);
    }

    public void sendOutgoingNotice(String target, String message){
        super.sendNotice(target, message);
    }

    public void sendOutgoingMessage(String target, String message){
        if(target != null) {
            super.sendMessage(target, message);
        }
    }

    public void onPrivateMessage(String sender, String login, String hostname,
                                 String message) {
        dispatchTable(sender, login, hostname, message);
    }

    private void dispatchTable(String sender, String login, String hostname,
                               String message) {
        cmd.dispatchTable(new CommandData("", sender, login, hostname, message));
    }

    public void onDisconnect() {
        int tries = 0;
        while (!this.isConnected()) {
            try {
                tries++;
                this.connect();
                if (this.isConnected()) {
                    this.joinChannels();
                }
            } catch (Exception e) {
                logger.error(e);
                if (tries > 10) {
                    logger.error("Shutting down HelenBot!");
                    System.exit(1);
                }
                try {
                    Thread.sleep(10000);
                } catch (Exception ex) {
                    logger.error(ex);
                }
            }
        }
    }


    public void onPart(String channel, String sender, String login,
                       String hostname) {

    }

    public void onQuit(String sourceNick,
                       String sourceLogin,
                       String sourceHostname,
                       String reason) {

    }

    public static void main(String[] args) throws IOException {
        Bans.updateBans();
        int i = 0;

    }

    public void onJoin(String channel, String sender, String login,
                       String hostmask) {

        checkBan(channel,sender,login,hostmask);
    }

    private boolean checkBan(String channel, String sender, String login,
                          String hostmask){
        try {
            BanInfo info = Bans.getUserBan(sender.toLowerCase(), hostmask.toLowerCase(), channel, login);
            if (info != null) { // We have a match in the ban page that is still active.

                //Kick them.
                kick(channel, sender, info.getReason());

                // Look at the hostmask from 05 and determine the appropriate banmask.
                // We do this because without prepending the mask with the appropriate wildcards,
                // pircbot sets an invalid mask.
                if(info.getHostmasks().isEmpty()){
                    ban(channel,sender);
                    timer.schedule(new TimerTask() {
                        @Override
                        public void run() {
                            unBan(channel, sender);
                        }
                    }, 900000);

                }else {
                    if (hostmask.contains("@")) { // We have a username to ban as well as a hostmask.
                        hostmask = "*!" + hostmask;
                    } else { // The more usual listing in 05, just the hostmask.
                        hostmask = "*!*@" + hostmask;
                    }
                    ban(channel, hostmask);
                    String finalHostmask = hostmask;
                    timer.schedule(new TimerTask() {
                        @Override
                        public void run() {
                            unBan(channel, finalHostmask);
                        }
                    }, 900000);
                }
                return true;
            }
        } catch (Exception e) {
            logger.error("Exception attempting onjoin for " + channel + " , " + sender + " " + login + " " + hostmask, e);
            return false;
        }
        return false;
    }

}
