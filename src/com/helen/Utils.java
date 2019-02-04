package com.helen;

import com.helen.error.*;
import io.github.cdimascio.dotenv.Dotenv;
import org.jibble.pircbot.Colors;

import javax.annotation.Nullable;
import java.lang.reflect.Array;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.regex.Pattern;

public final class Utils {
  public static final long NANO    = 1_000_000;
  public static final long MINUTES = 60_000L;
  public static final long HOURS   = 60 * MINUTES;
  public static final long DAYS    = 24 * HOURS;
  public static final long YEARS   = 365 * DAYS;
  private static final ZoneId TZ   = ZoneOffset.systemDefault();

  private static final Dotenv ENV  = Dotenv.configure().ignoreIfMissing().load();
  private static final Pattern GAP = Pattern.compile("[ ]{2,}");

  private static final Map<Character, Pattern> charPatterns = new HashMap<>();
  private static final Map<Character, Pattern> spacedCharPatterns = new HashMap<>();

  public static String findTime(long time) {
    time = System.currentTimeMillis() - time;
    if (time >= YEARS) {
      return measureTime(time / YEARS, "year");
    } else if (time >= DAYS) {
      return measureTime(time / DAYS, "day");
    } else if (time >= HOURS) {
      return measureTime(time / HOURS, "hour");
    } else if (time >= MINUTES) {
      return measureTime(time / MINUTES, "minute");
    } else {
      return "a few seconds ago";
    }
  }

  private static String measureTime(long amount, String label) {
    return amount + " " + label + (amount == 1 ? " " : "s ") + "ago";
  }

  public static long parseZonedTime(String str) {
    return ZonedDateTime.parse(str).withZoneSameInstant(TZ).toEpochSecond() * 1_000;
  }


  public static String fmtRating(int rating) {
    return (rating > 0 ? "+" : "") + rating;
  }

  public static boolean count(boolean comma, StringBuilder str, int size, String name) {
    if (size == 0) {
      return comma;
    } else {
      if (comma) {
        str.append(", ");
      }
      str .append(Colors.BOLD)
          .append(size)
          .append(Colors.NORMAL)
          .append(' ')
          .append(name);
      if (size != 1) {
        str.append('s');
      }
      return true;
    }
  }

  @SuppressWarnings("unchecked")
  public static <T> T[][] chunk(int cols, T[] xs) {
    int rows = xs.length % cols == 0 ? xs.length / cols : 1 + xs.length / cols;
    T[][] chunks = (T[][]) Array.newInstance(xs.getClass(), rows);
    int i;
    for (i = 0; i + 1 < chunks.length; i++) {
      chunks[i] = Arrays.copyOfRange(xs, cols * i, cols * (i + 1));
    }
    chunks[i] = Arrays.copyOfRange(xs, cols * i, xs.length);
    return chunks;
  }

  public static <V> V timed(String label, Consumer<String> logger, Callable<V> f) throws Exception {
    long start = System.nanoTime();
    V result = f.call();
    long finish = System.nanoTime();
    long elapsed = (finish - start) / NANO;
    logger.accept(label + " in " + elapsed + "ms.");
    return result;
  }
  public static void timed(String label, Consumer<String> logger, Runnable f) {
    long start = System.nanoTime();
    f.run();
    long finish = System.nanoTime();
    long elapsed = (finish - start) / NANO;
    logger.accept(label + " in " + elapsed + "ms.");
  }

  private static String escape(char c) {
    return "[" + (c == '[' || c == ']' || c == '\\' ? "\\" + c : c) + "]+";
  }
  public static String remove(char c, String str) {
    return charPatterns
        .computeIfAbsent(c, k -> Pattern.compile(escape(k)))
        .matcher(str)
        .replaceAll("");
  }
  public static String[] split(char c, String str) {
    return spacedCharPatterns
        .computeIfAbsent(c, k -> Pattern.compile("\\s*" + escape(k) + "\\s*"))
        .splitAsStream(str)
        .filter(x -> !x.isEmpty())
        .toArray(String[]::new);
  }

  public static String reduceSpaces(String str) {
    return GAP.matcher(str).replaceAll(" ");
  }

  @Nullable
  public static String envOption(String key) {
    String val = ENV.get(key);
    return val == null || val.isEmpty() ? null : val;
  }

  public static String env(String key) {
    String val = envOption(key);
    if (val == null) {
      throw new InvalidConfigurationException("Property '" + key + "' not found");
    }
    return val;
  }
}
