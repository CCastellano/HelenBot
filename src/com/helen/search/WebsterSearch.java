package com.helen.search;

import com.helen.*;
import com.helen.database.*;
import org.apache.log4j.Logger;
import org.jibble.pircbot.Colors;
import org.w3c.dom.DOMException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.annotation.Nullable;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.stream.Collectors;

public class WebsterSearch {
  private static final Logger logger = Logger.getLogger(WebsterSearch.class);

  private static final DocumentBuilderFactory FACTORY = DocumentBuilderFactory.newInstance();
  private static final String URL = "https://www.dictionaryapi.com/api/v1/references/collegiate/xml/";

  @Nullable
  public static String dictionarySearch(String query) throws ParserConfigurationException {
    query = query.toLowerCase();
    try {
      Document doc = findDefinition(query);
      if (doc == null) {
        return null;
      }
      NodeList sugList = doc.getElementsByTagName("suggestion");
      if (sugList.getLength() > 0) {
        doc = findDefinition(sugList.item(0).getFirstChild().getNodeValue());
        if (doc == null) {
          return null;
        } else {
          return processDocument(doc, sugList.item(0).getFirstChild().getNodeValue());
        }
      } else {
        return processDocument(doc, query);
      }
    } catch (DOMException e) {
      logger.warn("Exception dictionary searching", e);
      return null;
    }
  }

  @Nullable
  private static Document findDefinition(String keyword) throws ParserConfigurationException {
    try {
      URL url = new URL(URL + keyword + "?key=" + Configs.getSingleProperty("dictionaryKey").value);
      HttpURLConnection conn = (HttpURLConnection) url.openConnection();
      conn.setRequestMethod("GET");
      try (BufferedReader rd = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
        String result = rd.lines().collect(Collectors.joining());
        if ("Results not found".equals(result)) {
          return null;
        }
        InputSource is = new InputSource(new StringReader(result));
        return FACTORY.newDocumentBuilder().parse(is);
      }
    } catch (IOException | SAXException e) {
      logger.warn("There was an exception attempting to retrieve dictionary results", e);
    }

    return null;
  }

  private static String processDocument(Document doc, String keyword) {
    NodeList list = doc.getElementsByTagName("entry");
    if (list.getLength() > 0) {
      String alternateForm =
          ((Element) list.item(0))
              .getElementsByTagName("ew")
              .item(0)
              .getFirstChild()
              .getNodeValue();
      Collection<Node> nodesToAnalyze = new ArrayList<>();
      try {
        for (int i = 0; i < list.getLength(); i++) {
          NodeList nl = ((Element) list.item(i)).getElementsByTagName("ew");
          for (int j = 0; j < nl.getLength(); j++) {
            String value = nl.item(j).getFirstChild().getNodeValue();
            if (keyword.equals(value) || alternateForm.equals(value)) {
              nodesToAnalyze.add(list.item(i));
            }
          }
        }
      } catch (DOMException e) {
        logger.warn("Error searching definition", e);
      }

      Collection<Definition> test = new ArrayList<>(nodesToAnalyze.size());
      for (Node n : nodesToAnalyze) {
        Element nodeler = (Element) n;
        String pos = nodeler.getElementsByTagName("fl").item(0).getFirstChild().getNodeValue();
        Definition def = new Definition(pos);
        NodeList defs = ((Element) nodeler.getElementsByTagName("def").item(0)).getElementsByTagName("dt");
        for (int i = 0; i < defs.getLength(); i++) {
          NodeList sxList = ((Element) defs.item(i)).getElementsByTagName("sx");
          NodeList fwList = ((Element) defs.item(i)).getElementsByTagName("fw");
          NodeList testList = defs.item(i).getChildNodes();
          String definition;
          if (sxList.getLength() > 0) {
            definition = testList.item(1).getFirstChild().getNodeValue();
          } else if (fwList.getLength() > 0) {
            definition = Utils.remove(':', defs.item(i).getFirstChild().getNodeValue()) +
                         Utils.remove(':', testList.item(1).getFirstChild().getNodeValue());
          } else {
            definition = Utils.remove(':', defs.item(i).getFirstChild().getNodeValue());
          }
          if (!definition.trim().isEmpty()) {
            def.definitions.add(definition);
          }
        }
        test.add(def);
      }
      StringBuilder str = new StringBuilder(Colors.BOLD)
          .append(alternateForm)
          .append(" -")
          .append(Colors.NORMAL);
      for (Definition d : test) {
        str .append(' ')
            .append(Colors.BOLD)
            .append(d.partOfSpeech)
            .append(": ")
            .append(Colors.NORMAL);
        for (int i = 0; i < 2 && i < d.definitions.size(); i++) {
          if (i > 0) {
            str.append(' ');
          }
          str.append(i + 1);
          str.append(". ");
          str.append(d.definitions.get(i));
        }
      }
      return str.toString();
    } else {
      return "I couldn't find a definition for " + keyword + '.';
    }
  }
}
