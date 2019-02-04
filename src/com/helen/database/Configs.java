package com.helen.database;

import com.helen.*;
import com.helen.commands.*;
import com.helen.error.*;
import org.apache.log4j.Logger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class Configs {
  private static final Logger logger = Logger.getLogger(Configs.class);

  private static final long MINUTES_TO_MILLIS = 60_000;

  private static final Map<String, Config> environmentVars = new HashMap<>();
  private  static final Map<String, List<Config>> environmentMultiVars = new HashMap<>();
  private static final Map<String, List<Config>> cachedProperties = new HashMap<>();
  private static boolean cacheValid = false;

  public static List<Config> getProperty(String key) {
    String keyUp = key.toUpperCase();
    List<Config> fromEnv = environmentMultiVars.computeIfAbsent(keyUp, k -> {
      String value = Utils.envOption(k);
      return value == null ? null : Arrays
          .stream(Utils.split(',', value))
          .map(v -> new Config(k, v, false))
          .collect(Collectors.toList());
    });
    if (fromEnv != null) {
      return fromEnv;
    }
    if (!cacheValid) {
      loadProperties();
    }
    List<Config> configs = cachedProperties.get(keyUp.toUpperCase());
    if (configs == null) {
      logger.warn("Attempt to access unknown property: " + key);
      return new ArrayList<>();
    } else {
      return configs;
    }
  }

  public static void clear() {
    cacheValid = false;
    loadProperties();
  }

  public static Config getSingleProperty(String key) {
    String keyUp = key.toUpperCase();
    Config fromEnv = environmentVars.computeIfAbsent(keyUp, k -> {
      String value = Utils.envOption(k);
      return value == null ? null : new Config(k, value, false);
    });
    if (fromEnv != null) {
      return fromEnv;
    } else {
      if (!cacheValid) {
        loadProperties();
      }
      List<Config> props = cachedProperties.get(keyUp);
      if (props == null || props.isEmpty()) {
        throw new InvalidConfigurationException("Property '" + key + "' not found");
      }
      return props.get(0);
    }
  }

  public static String setProperty(String key, String value, boolean publicFlag) {
    try (
        Connection conn = Connector.getConnection();
        PreparedStatement stmt = Connector.prepare(conn, "propertySet",
            "INSERT INTO properties (key, value, public) VALUES (?, ?, ?)",
            key, value, publicFlag
        )
    ) {
      if (stmt.executeUpdate() > 0) {
        loadProperty(new Config(key, value, publicFlag));
        return "Property " + key + " has been set to " + value;
      }
    } catch (SQLException e) {
      logger.error("Exception attempting to set property", e);
    }
    return "There was an error during the update process. Please check the logs.";
  }

  public static String updateSingle(String key, String value, boolean publicFlag) {
    try (
        Connection conn = Connector.getConnection();
        PreparedStatement stmt = Connector.prepare(conn, "updateCheck",
            "SELECT COUNT(*) as counted FROM properties WHERE key = ?",
            key
        );
        ResultSet rs = stmt.executeQuery()
    ) {
      if (!rs.next()) {
        logger.error("Exception attempting to set property. The returned result set had no values.");
      } else if (rs.getInt("counted") > 1) {
        return "That property has multiple values. Please contact " +
               Configs.getSingleProperty("contact") + " to have it modified.";
      } else if (rs.getInt("counted") < 1) {
        return "That property currently is not set. This operation doesn't support insertion.";
      } else {
        try (
            PreparedStatement updateStatement = Connector.prepare(conn, "updatePush",
                "UPDATE properties SET value = ?, public = ?, updated = current_timestamp " +
                "WHERE key = ?",
                value, publicFlag, key
            )
        ) {
          if (updateStatement.executeUpdate() > 0) {
            List<Config> singleton = new ArrayList<>();
            singleton.add(new Config(key, value, publicFlag));
            cachedProperties.put(key.toUpperCase(), singleton);
            return "Updated " + key + " to value " + value;
          } else {
            return "I'm sorry, there was an error updating the key specified.";
          }
        }
      }
    } catch (SQLException e) {
      logger.error("Exception attempting to set property.", e);
    }

    return "There was an error during the update process. Please check the logs.";
  }

  public static String removeProperty(String key, String value) {
    Collection<Config> configs = cachedProperties.get(key.toUpperCase());
    if (configs != null && configs.removeIf(c -> c.value.equalsIgnoreCase(value))) {
      try (
          Connection conn = Connector.getConnection();
          PreparedStatement stmt = Connector.prepare(conn, "deleteConfig",
              "DELETE FROM properties WHERE key = ? AND value = ?",
              key, value
          )
      ) {
        if (stmt.executeUpdate() > 0) {
          return "Successfully removed " + key + " with the value " + value +
                 " from the properties table.";
        } else {
          return "There was an error removing the specified key/value pair.";
        }
      } catch (SQLException e) {
        logger.error("Exception deleting property.", e);
      }
    } else {
      return "Sorry, this property is either not currently configured or not publically accessible.";
    }
    return "There was an unexpected error attempting to delete property.";
  }

  private static void loadProperty(Config c) {
    cachedProperties.computeIfAbsent(c.key.toUpperCase(), k -> new ArrayList<>()).add(c);
  }

  private static void loadProperties() {
    cachedProperties.clear();
    try (
        Connection conn = Connector.getConnection();
        PreparedStatement stmt = Connector.prepare(conn, "kvQuery",
            "SELECT * FROM properties"
        );
        ResultSet rs = stmt.executeQuery()
    ) {
      while (rs.next()) {
        loadProperty(new Config(rs));
      }
      cacheValid = true;
    } catch (SQLException e) {
      logger.error("Exception attempting to retrieve properties list", e);
    }
  }

  public static Stream<Config> getConfiguredProperties(boolean showPublic) {
    if (!cacheValid) {
      loadProperties();
    }
    return Stream.concat(
        environmentVars.values().stream(),
        Stream.concat(
            environmentMultiVars.values().stream(),
            cachedProperties.values().stream()
        ).flatMap(Collection::stream)
    ) .filter(value -> !showPublic || value.isPublic);
  }


  public static boolean commandEnabled(CommandData data, String command) {
    if (data.channel == null) {
      return true;
    } else {
      try (
          Connection conn = Connector.getConnection();
          PreparedStatement stmt = Connector.prepare(conn, "commandEnabled",
              "SELECT enabled FROM channeltoggles WHERE channel = ? AND command = ?",
              data.channel.toLowerCase(), command
          );
          ResultSet rs = stmt.executeQuery()
      ) {
        return rs.next() && rs.getBoolean("enabled");
      } catch (SQLException e) {
        logger.error("Couldn't get toggle", e);
        return false;
      }
    }
  }

  public static String insertToggle(CommandData data, String command, boolean enabled) {
    if (data.channel == null) {
      return Command.CHANNEL_ONLY;
    } else {
      try (
          Connection conn = Connector.getConnection();
          PreparedStatement stmt = Connector.prepare(conn, "insertToggle",
              "INSERT INTO channeltoggles (channel, command, enabled) VALUES (?, ?, ?) " +
              "ON CONFLICT (channel, command) DO UPDATE SET enabled = excluded.enabled",
              data.channel.toLowerCase(), command.toLowerCase(), enabled
          )
      ) {
        stmt.executeUpdate();
      } catch (SQLException e) {
        if (e.getMessage().contains("channel_unique")) {
          return updateToggle(data, command, enabled);
        } else {
          logger.error("Error inserting a toggle", e);
        }
      }
      return "Set " + command + " to " + enabled + " for " + data.channel;
    }
  }

  public static String updateToggle(CommandData data, String command, boolean enabled) {
    if (data.channel == null) {
      return Command.CHANNEL_ONLY;
    } else {
      try (
          Connection conn = Connector.getConnection();
          PreparedStatement stmt = Connector.prepare(conn, "updateToggle",
              "UPDATE channeltoggles SET enabled = ? WHERE channel = ? AND command = ?",
              enabled, data.channel.toLowerCase(), command.toLowerCase()
          )
      ) {
        stmt.executeUpdate();
      } catch (SQLException e) {
        logger.error("Couldn't get toggle", e);
        return "There was an error attempting to update toggle";
      }
      return "Updated " + data.getCommand() + " to " + enabled + " for " + data.channel;
    }
  }

  public static long getTimer(String key) {
    try (
        Connection conn = Connector.getConnection();
        PreparedStatement stmt = Connector.prepare(conn, "getTimeout",
            "SELECT minutes FROM timers WHERE name = ?",
            key
        );
        ResultSet rs = stmt.executeQuery()
    ) {
      if (rs.next()) {
        return MINUTES_TO_MILLIS * rs.getInt("minutes");
      }
    } catch (SQLException e) {
      logger.error("Error getting timeout property", e);
    }
    throw new InvalidConfigurationException("Timer '" + key + "' not found");
  }
}
