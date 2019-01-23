package com.helen.database.selectable;

import com.helen.commands.CommandData;
import com.helen.search.WikipediaSearch;

import java.io.IOException;

public class WikipediaAmbiguous implements Selectable {

  private final CommandData data;
  private final String title;

  public WikipediaAmbiguous(CommandData data, String title){
    this.data = data;
    this.title = title;
  }

  @Override
  public String getDisplay(){
    return this.title.replaceAll(",", "");
  }

  @Override
  public String run() throws IOException {
    return WikipediaSearch.search(this.data, this.title);
  }
}
