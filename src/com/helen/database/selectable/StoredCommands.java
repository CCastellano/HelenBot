package com.helen.database.selectable;

import com.helen.commands.*;
import org.apache.log4j.Logger;
import org.jibble.pircbot.Colors;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class StoredCommands {
  private static final Logger logger = Logger.getLogger(StoredCommands.class);

  private static final int CHARACTER_LIMIT = 350;
  private static final Map<String, List<Selectable>> store = new HashMap<>();

  private static String didYouMean(List<Selectable> choices) {
    int size = choices.size();
    StringBuilder str = new StringBuilder("Did you mean:");
    for (int i = 0; i < size && str.length() < CHARACTER_LIMIT; i++) {
      if (i > 0) {
        str.append(',');
      }
      str .append(' ')
          .append(Colors.BOLD)
          .append(i + 1)
          .append('.')
          .append(Colors.NORMAL)
          .append(' ')
          .append(choices.get(i).getDisplay());
    }
    return str
        .append(" (")
        .append(Colors.BOLD)
        .append(size)
        .append(Colors.NORMAL)
        .append(" total)")
        .toString();
  }

  public static String store(String user, List<Selectable> choices) {
    if (choices.isEmpty()) {
      return Command.NOT_FOUND;
    } else {
      store.put(user.toLowerCase(), choices);
      return didYouMean(choices);
    }
  }

  @Nullable
  public static String run(String index, String username) {
    try {
      return store.get(username.toLowerCase()).get(Integer.parseInt(index) - 1).run();
    } catch (Exception e) {
      logger.warn("There was an exception getting stored info", e);
    }
    return "That isn't a valid option.";
  }
}
