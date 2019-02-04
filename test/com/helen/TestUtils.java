package com.helen;

import com.helen.bots.*;
import org.junit.Assert;
import org.junit.Test;

public class TestUtils {
  @Test
  public void count() {
    StringBuilder str = new StringBuilder();
    boolean comma = Utils.count(false, str, 5, "SCP article");
    comma = Utils.count(comma, str, 6, "tale");
    comma = Utils.count(comma, str, 1, "GOI article");
    comma = Utils.count(comma, str, 0, "hub");
    Utils.count(comma, str, 8, "artwork page");
    Assert.assertEquals("Invalid output format",
        "5 SCP articles, 6 tales, 1 GOI article, 8 artwork pages",
        TestBot.CLEAN.matcher(str.toString()).replaceAll("")
    );
  }

  @Test
  public void remove() {
    Assert.assertEquals("Failed to remove 'c' properly",
        "a b de ",
        Utils.remove('c', "a b cdecc c")
    );
  }

  @Test
  public void split() {
    Assert.assertArrayEquals("Failed to split around 'c' properly",
        new String[]{"a b", "de"},
        Utils.split('c', "a b cdecc c")
    );
  }
}
