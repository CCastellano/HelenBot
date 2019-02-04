package com.helen.search;

import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.SearchListResponse;
import com.google.api.services.youtube.model.SearchResult;
import com.google.api.services.youtube.model.Video;
import com.google.api.services.youtube.model.VideoListResponse;
import com.helen.database.*;
import org.apache.log4j.Logger;
import org.jibble.pircbot.Colors;

import javax.annotation.Nullable;
import java.io.IOException;
import java.math.BigInteger;
import java.util.List;
import java.util.regex.Pattern;

public final class YouTubeSearch {
  private static final Logger logger = Logger.getLogger(YouTubeSearch.class);

  private static final Pattern PT = Pattern.compile("PT");
  private static final YouTube.Builder BUILDER = new YouTube.Builder(
      new NetHttpTransport(),
      new JacksonFactory(),
      request -> {}
  ).setApplicationName("youtube-cmdline-search-sample");

  private static BigInteger orZero(@Nullable BigInteger i) {
    return i == null ? BigInteger.ZERO : i;
  }

  @Nullable
  public static String youtubeSearch(String searchTerm) {

    YouTube youtube = BUILDER.build();

    try {
      YouTube.Search.List search = youtube.search().list("id,snippet");

      search.setKey(Configs.getSingleProperty("apiKey").value);
      search.setQ(searchTerm);
      search.setType("video");
      search.setFields("items(id/kind,id/videoId,snippet/title,snippet/thumbnails/default/url)");
      search.setMaxResults(1L);

      SearchListResponse searchResponse = search.execute();
      List<SearchResult> searchResultList = searchResponse.getItems();
      if (searchResultList == null || searchResultList.isEmpty()) {
        return null;
      }
      SearchResult video = searchResultList.get(0);

      StringBuilder str = new StringBuilder();

      YouTube.Videos.List videoRequest = youtube.videos().list("snippet, statistics, contentDetails");
      videoRequest.setId(video.getId().getVideoId());
      videoRequest.setKey(Configs.getSingleProperty("apiKey").value);
      VideoListResponse listResponse = videoRequest.execute();
      List<Video> videoList = listResponse.getItems();
      if (videoList.isEmpty()) {
        return null;
      }
      Video targetVideo = videoList.get(0);
      BigInteger views;
      BigInteger rating;
      BigInteger dislikes;
      if (targetVideo.getStatistics() != null) {
        views    = orZero(targetVideo.getStatistics().getViewCount());
        rating   = orZero(targetVideo.getStatistics().getLikeCount());
        dislikes = orZero(targetVideo.getStatistics().getDislikeCount());
      } else {
        views    = BigInteger.ZERO;
        rating   = BigInteger.ZERO;
        dislikes = BigInteger.ZERO;
      }
      String[] duration = PT.split(targetVideo.getContentDetails().getDuration());
      if (duration.length <= 1) {
        logger.warn("Bad duration format: " + targetVideo.getContentDetails().getDuration());
        return null;
      }
      String time = duration[1].toLowerCase();
      String uploader = targetVideo.getSnippet().getChannelTitle();

      return str
          .append(Colors.BOLD)
          .append(video.getSnippet().getTitle())
          .append(Colors.NORMAL)
          .append(" -  length ")
          .append(Colors.BOLD)
          .append(time)
          .append(Colors.NORMAL)
          .append(" - ")
          .append(rating)
          .append('↑')
          .append(dislikes)
          .append('↓')
          .append(" - ")
          .append(Colors.BOLD)
          .append(views)
          .append(Colors.NORMAL)
          .append(" views")
          .append(" - ")
          .append(Colors.BOLD)
          .append(uploader)
          .append(Colors.NORMAL)
          .append(" - ")
          .append("https://www.youtube.com/watch?v=")
          .append(video.getId().getVideoId())
          .toString();
    } catch (IOException e) {
      logger.warn("There was an exception attempting to search YouTube", e);
    }
    return null;
  }
}
