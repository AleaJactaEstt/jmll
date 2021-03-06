package com.expleague.crawl.data.impl;

import com.expleague.crawl.data.Message;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * User: qdeee
 * Date: 03.04.16
 */
public class InputCommandMessage implements Message {
  @JsonProperty("text")
  private final String text;

  public InputCommandMessage(KeyCommand keyCommand) {
    this.text = keyCommand.getText();
  }

  public InputCommandMessage(char keyCommand) {
    this.text = Character.toString(keyCommand);
  }

  public InputCommandMessage(String wordCommand) {
    this.text = wordCommand;
  }
}
