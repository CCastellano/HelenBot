package com.helen.commands;

import com.helen.*;
import com.helen.bots.*;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;

public class TestCommand {
  private static final int FUZZ = 100; // Number of loops for randomized tests.

  @Test
  public void authorSearch() {
    TestBot bot = new TestBot();
    String au = bot.search(".au djkaktus");
    Assert.assertNotNull("No results found for .au", au);
    String search = bot.search(".s -a djkaktus -t tale -u");
    Assert.assertNotNull("No results found for .s", search);
    Assert.assertEquals("Mismatched author results:\n" + au + '\n' + search,
        Utils.split(' ', au)[11],
        Utils.split(' ', search)[2]
    );
  }

  @Test
  public void toggleMode() {
    TestBot bot = new TestBot();
    bot.run(".modeToggle");
    bot.assertResponse("<Sender>: I am now in Admin Only mode.");
    bot.run(".mode");
    bot.assertResponse("<Sender>: I am currently in Admin Only mode.");
    bot.run(".modeToggle");
    bot.assertResponse("<Sender>: I am now in Any User mode.");
    bot.run(".mode");
    bot.assertResponse("<Sender>: I am currently in Any User mode.");
    bot.run(".modeToggle");
    bot.assertResponse("<Sender>: I am now in Admin Only mode.");
    bot.run(".mode");
    bot.assertResponse("<Sender>: I am currently in Admin Only mode.");
  }

  @Test
  public void chooseMultiple() {
    TestBot bot = new TestBot();
    Collection<String> chosen = new HashSet<>();
    for (int i = 0; i < FUZZ; i++) {
      bot.run(".ch A, B, C");
      chosen.add(bot.getResponses().get(0));
    }
    Assert.assertEquals("Incorrect choices",
        new HashSet<>(Arrays.asList("<Sender>: A", "<Sender>: B", "<Sender>: C")),
        chosen);
  }
  @Test
  public void chooseSingle() {
    TestBot.assertRun(".ch A", "<Sender>: A");
  }
  @Test
  public void chooseNothing() {
    TestBot.assertRun(".ch", "<Sender>: I choose nothing.");
  }

  @Test
  public void rollFudge() {
    TestBot.assertRun(".roll 0dF -e Test Message", "<Sender>: Test Message: 0 (0dF=0: [])");
  }
  @Test
  public void rollPercentile() {
    TestBot.assertRun(".roll 0d% + 0d10 + 2", "<Sender>: 2 (0d100=0, 0d10=0)");
  }
  @Test
  public void rollTooMany() {
    TestBot.assertRun(".roll 5001d1", "<Sender>: Cannot roll more than 5000 dice at a time.");
  }

  @Test
  public void sendMessage() {
    TestBot.assertRun(".msg ChanServ Hello, friend.",
        "@ChanServ: <Sender> said: Hello, friend.");
  }

  @Test
  public void webSearch() {
    Assert.assertNotNull("No results found", new TestBot().search(".g scp"));
  }
  @Test
  public void webSearchFail() {
    Assert.assertNull("False positive", new TestBot().search(".g !!"));
  }

  @Test
  public void webSearchImage() {
    Assert.assertNotNull("No results found", new TestBot().search(".gis scp"));
  }
  @Test
  public void webSearchImageFail() {
    Assert.assertNull("False positive", new TestBot().search(".gis !!"));
  }

  @Test
  public void wikipediaSearch() {
    TestBot.assertRun(".w Monty Oum",
        "<Sender>: https://en.wikipedia.org/wiki/Monty%20Oum - Monyreak \"Monty\" Oum was an " +
        "American web-based animator and writer. A self-taught animator, he scripted and produced " +
        "several crossover fighting video series, drawing the attention of internet production " +
        "company Rooster Teeth, who hired him. There, he provided custom animations for Red vs. " +
        "Blue, and […]");
  }
  @Test
  public void WikipediaSearchFail() {
    Assert.assertNull("False positive", new TestBot().search(".w ``"));
  }
  @Test
  public void WikipediaSearchAmbiguous() {
    String result = new TestBot().search(".w rock");
    Assert.assertTrue("Failed to disambiguate",
        result != null && result.startsWith("<Sender>: Did you mean:"));
  }
  @Test
  public void youtubeSearch() {
    Assert.assertNotNull("No results found", new TestBot().search(".y scp"));
  }
  @Test
  public void youtubeSearchFail() {
    Assert.assertNull("False positive", new TestBot().search(".y !!"));
  }

  @Test
  public void help() {
    TestBot.assertRun(".help",
        "<Sender>: You can find a list of my job responsibilities here: " +
        "http://home.helenbot.com/usage.html");
  }

  @Test
  public void lastCreated() {
    TestBot bot = new TestBot();
    bot.run(".lc");
    Collection<String> responses = bot.getResponses();
    Assert.assertEquals("Wrong number of responses", 3, responses.size());
  }

  @Test
  public void define() {
    TestBot.assertRun(".def revolvers",
        "<Sender>: revolver - noun: 1. one that revolves 2. a handgun with a cylinder of several " +
        "chambers brought successively into line with the barrel and discharged with the same " +
        "hammer");
  }

  @Test
  public void getUserName() {
    TestBot.assertRun(".user helen", "<Sender>: http://www.wikidot.com/user:info/helen");
  }
}
