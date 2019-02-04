package com.helen.database.selectable;

import com.helen.*;
import com.helen.commands.*;
import com.helen.search.*;

public class WikipediaAmbiguous implements Selectable {

  private final CommandData data;
  private final String title;

  public WikipediaAmbiguous(CommandData data, String title) {
    this.data  = data;
    this.title = title;
  }

  @Override
  public String getDisplay() {
    return Utils.remove(',', title);
  }

  @Override
  public String run() {
    return WikipediaSearch.search(data, title);
  }
}
