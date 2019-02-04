package com.helen.search;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.helen.database.*;
import org.apache.log4j.Logger;

import javax.annotation.Nullable;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

public final class WebSearch {
  private static final Logger logger = Logger.getLogger(WebSearch.class);

  private static final String URL = "https://www.googleapis.com/customsearch/v1?key=";

  @Nullable
  private static GoogleResults eitherSearch(String searchTerm, boolean image) {
    try {
      URL url = new URL(URL +
                        Configs.getSingleProperty("apiKey").value +
                        "&cx=" + Configs.getSingleProperty("customEngine").value +
                        "&q=" + URLEncoder.encode(searchTerm, "UTF-8") +
                        "&alt=json" +
                        (image ? "&searchType=image" : ""));
      HttpURLConnection conn = (HttpURLConnection) url.openConnection();
      conn.setRequestMethod("GET");
      conn.setRequestProperty("Accept", "application/json");
      GoogleResults searchResult = null;
      try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
        JsonParser json = new JsonParser();
        JsonElement jsonTree = json.parse(br);
        if (jsonTree != null && jsonTree.isJsonObject()) {
          JsonObject jsonObject = jsonTree.getAsJsonObject();

          JsonElement items = jsonObject.get("items");
          if (items != null && items.isJsonArray()) {
            JsonArray itemsArray = items.getAsJsonArray();
            if (itemsArray.size() > 0) {
              JsonElement result = itemsArray.get(0);
              if (result != null && result.isJsonObject()) {
                JsonObject resultMap = result.getAsJsonObject();
                searchResult = new GoogleResults(resultMap, !image);
              }
            }
          }
        }
      }
      conn.disconnect();
      return searchResult;
    } catch (IOException e) {
      logger.warn("Error while searching for " + searchTerm, e);
      return null;
    }
  }

  @Nullable
  public static GoogleResults search(String searchTerm) {
    return eitherSearch(searchTerm, false);
  }

  @Nullable
  public static GoogleResults imageSearch(String searchTerm) {
    return eitherSearch(searchTerm, true);
  }
}
