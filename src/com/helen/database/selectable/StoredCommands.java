package com.helen.database.selectable;

import com.helen.commands.Command;
import org.apache.log4j.Logger;
import org.jibble.pircbot.Colors;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class StoredCommands {
  private static final Logger logger = Logger.getLogger(StoredCommands.class);

  private static final int CHARACTER_LIMIT = 350;
  private static final HashMap<String, ArrayList<Selectable>> store = new HashMap<>();

  private static String didYouMean(List<Selectable> choices){
    int size = choices.size();
    StringBuilder str = new StringBuilder("Did you mean:");
    for(int i = 0; i < size && str.length() < CHARACTER_LIMIT; i++) {
      if (i > 0)
        str.append(",");
      str.append(" ");
      str.append(Colors.BOLD);
      str.append(i + 1);
      str.append(".");
      str.append(Colors.NORMAL);
      str.append(" ");
      str.append(choices.get(i).getDisplay());
    }
    str.append(" (");
    str.append(Colors.BOLD);
    str.append(size);
    str.append(Colors.NORMAL);
    str.append(" total)");
    return str.toString();
  }

  public static String store(String user, ArrayList<Selectable> choices) {
    if (choices.isEmpty()) {
      return Command.NOT_FOUND;
    } else {
      store.put(user.toLowerCase(), choices);
      return didYouMean(choices);
    }
  }

  public static String run(String index, String username) {
    try {
      return store.get(username.toLowerCase()).get(Integer.parseInt(index) - 1).run();
    } catch (Exception e) {
      logger.error("There was an exception getting stored info", e);
    }
    return "I'm sorry, that isn't one of the options.";
  }
}
