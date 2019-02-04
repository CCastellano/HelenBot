package com.helen.search;

import com.google.gson.JsonObject;
import org.jibble.pircbot.Colors;

import java.util.regex.Pattern;

public class GoogleResults {
  private static final Pattern CLEAN = Pattern.compile("[\"\\n]");

  private final String title;
  private final String link;
  private final String snippet;
  private final boolean full;

  public GoogleResults(JsonObject object, boolean full) {
    this.title   = CLEAN.matcher(object.get("title").toString()).replaceAll("");
    this.link    = CLEAN.matcher(object.get("link").toString()).replaceAll("");
    this.snippet = CLEAN.matcher(object.get("snippet").toString()).replaceAll("");
    this.full    = full;
  }

  @Override
  public String toString() {
    StringBuilder str = new StringBuilder(link)
        .append(link)
        .append(" - ")
        .append(Colors.BOLD)
        .append(title)
        .append(Colors.NORMAL);
    if (full) {
      str .append(": ")
          .append(snippet);
    }
    return str.toString();
  }
}
