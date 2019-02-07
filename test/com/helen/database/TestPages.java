package com.helen.database;

import com.helen.database.selectable.*;
import org.apache.xmlrpc.XmlRpcException;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;

public class TestPages {
  private static final int PAGE_LIMIT = 20;

  @Test
  public void walk() throws XmlRpcException {
    String[] titles = Pages.listPages();
    if (titles.length > PAGE_LIMIT) {
      titles = Arrays.copyOfRange(titles, 0, PAGE_LIMIT);
    }
    Collection<Page> pages = new ArrayList<>();
    Pages.walk(titles, (page, tags) -> pages.add(page));
    Assert.assertEquals("Wrong number of page results", titles.length, pages.size());
  }
}
