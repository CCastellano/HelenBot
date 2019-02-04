package com.helen.search;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.helen.*;
import com.helen.commands.*;
import com.helen.database.selectable.*;
import org.apache.log4j.Logger;

import javax.annotation.Nullable;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class WikipediaSearch {

  private static final Logger logger = Logger.getLogger(WikipediaSearch.class);

  private static final String SEARCH =
      "https://en.wikipedia.org/w/api.php?format=json&formatversion=2" +
      "&action=query&list=search&srlimit=1&srprop=&srsearch=";
  private static final String ENTRY  =
      "https://en.wikipedia.org/w/api.php?format=json" +
      "&action=query&prop=extracts|links&pllimit=500&exintro&explaintext&redirects=1&pageids=";

  private static final int CHARACTER_LIMIT = 300;
  private static final Pattern PLUS  = Pattern.compile("\\+");
  private static final Pattern CLEAN = Pattern.compile("\\s*\\([^()]+\\)");

  private static String wikiEncode(String unencoded) throws IOException {
    return PLUS.matcher(URLEncoder.encode(unencoded, "UTF-8")).replaceAll("%20");
  }

  private static String cleanContent(String content) {
    content = Utils.reduceSpaces(CLEAN.matcher(content).replaceAll(""));
    if (content.length() <= CHARACTER_LIMIT) {
      return content;
    } else {
      content = content.substring(0, CHARACTER_LIMIT);
      int lastWord = content.lastIndexOf(' ');
      return content.substring(0, lastWord != -1 ? lastWord + 1 : CHARACTER_LIMIT - 3) + "[…]";
    }
  }

  private static int getPage(String searchTerm) throws IOException {
    URL url = new URL(SEARCH + searchTerm);
    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
    conn.setRequestMethod("GET");
    conn.setRequestProperty("Accept", "application/json");
    int page = -1;
    try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
      JsonParser json = new JsonParser();
      JsonElement jsonTree = json.parse(br);
      if (jsonTree != null && jsonTree.isJsonObject()) {
        JsonElement query = jsonTree.getAsJsonObject().get("query");
        if (query != null && query.isJsonObject()) {
          JsonElement search = query.getAsJsonObject().get("search");
          if (search != null && search.isJsonArray()) {
            JsonArray results = search.getAsJsonArray();
            if (results != null && results.size() > 0) {
              JsonElement result = results.get(0);
              if (result != null && result.isJsonObject()) {
                JsonElement pageid = result.getAsJsonObject().get("pageid");
                if (pageid != null && pageid.isJsonPrimitive()) {
                  page = pageid.getAsInt();
                }
              }
            }
          }
        }
      }
    }
    conn.disconnect();
    return page;
  }

  @Nullable
  public static String search(CommandData data, String searchTerm) {
    try {
      int page = getPage(wikiEncode(searchTerm));
      if (page == -1) {
        return null;
      }
      String pageString = "" + page;
      URL url = new URL(ENTRY + pageString);
      HttpURLConnection conn = (HttpURLConnection) url.openConnection();
      conn.setRequestMethod("GET");
      conn.setRequestProperty("Accept", "application/json");
      String link = null;
      String content = null;
      List<String> disambiguate = new ArrayList<>();
      List<String> verbatim = new ArrayList<>();
      try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {

        JsonParser json = new JsonParser();
        JsonElement jsonTree = json.parse(br);
        if (jsonTree != null && jsonTree.isJsonObject()) {
          JsonElement query = jsonTree.getAsJsonObject().get("query");
          if (query != null && query.isJsonObject()) {
            JsonElement search = query.getAsJsonObject().get("pages");
            if (search != null && search.isJsonObject()) {
              JsonElement result = search.getAsJsonObject().get(pageString);
              if (result != null && result.isJsonObject()) {
                JsonObject resultObj = result.getAsJsonObject();
                JsonElement title = resultObj.get("title");
                if (title != null && title.isJsonPrimitive()) {
                  link = "https://en.wikipedia.org/wiki/" + wikiEncode(title.getAsString()) + " -";
                }
                JsonElement extract = resultObj.get("extract");
                if (extract != null && extract.isJsonPrimitive()) {
                  content = extract.getAsString();
                  String top = content;
                  content = content.replace('\n', ' ');
                  int newline = top.indexOf('\n');
                  if (newline != -1) {
                    top = top.substring(0, newline);
                  }
                  if (top.endsWith(":") && top.contains("refer")) {
                    JsonElement links = resultObj.get("links");
                    if (links != null && links.isJsonArray()) {
                      String ambig = searchTerm.toLowerCase() + " (";
                      for (JsonElement sublink : links.getAsJsonArray()) {
                        if (sublink.isJsonObject()) {
                          JsonElement subtitle = sublink.getAsJsonObject().get("title");
                          if (subtitle != null && subtitle.isJsonPrimitive()) {
                            String subtitleString = subtitle.getAsString();
                            if (subtitleString != null && !subtitleString.contains("disambiguation")) {
                              disambiguate.add(subtitleString);
                              if (subtitleString.toLowerCase().startsWith(ambig)) {
                                verbatim.add(subtitleString);
                              }
                            }
                          }
                        }
                      }
                    }
                  }
                }
              }
            }
          }
        }
      }

      conn.disconnect();

      if (content == null) {
        return null;
      } else if (disambiguate.isEmpty()) {
        return link + ' ' + cleanContent(content);
      } else if (verbatim.isEmpty()) {
        return disambiguateWikipedia(data, disambiguate);
      } else {
        return disambiguateWikipedia(data, verbatim);
      }
    } catch (IOException e) {
      logger.warn("Error looking up on Wikipedia", e);
      return null;
    }
  }

  public static String disambiguateWikipedia(CommandData data, List<String> titles) {
    if (titles.isEmpty()) {
      return "I couldn't find any choices.";
    } else {
      List<Selectable> choices = titles
          .stream()
          .map(title -> new WikipediaAmbiguous(data, title))
          .collect(Collectors.toList());
      return StoredCommands.store(data.sender, choices);
    }
  }
}
