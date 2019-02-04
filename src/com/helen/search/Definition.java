package com.helen.search;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

public class Definition {
  public final List<String> definitions;
  @Nullable public final String partOfSpeech;

  public Definition(@Nullable String partOfSpeech) {
    this.definitions = new ArrayList<>();
    this.partOfSpeech = partOfSpeech;
  }
}
