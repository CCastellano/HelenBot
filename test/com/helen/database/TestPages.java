package com.helen.database;

import com.helen.database.selectable.*;
import org.apache.xmlrpc.XmlRpcException;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;

public class TestPages {

  @Test
  public void walk() throws XmlRpcException {
    String[] list = Pages.listPages();
    Collection<Page> pages = new ArrayList<>();
    Collection<String> tags = new ArrayList<>();
    Pages.walk(Arrays.copyOfRange(list, 0, StrictMath.min(20, list.length)), (page, tagList) -> {
      pages.add(page);
      tags.addAll(Arrays.asList(tagList));
    });
    Assert.assertEquals("Wrong number of page results", 20, pages.size());
    Assert.assertNotEquals("No tags found", 0, tags.size());
  }
}
