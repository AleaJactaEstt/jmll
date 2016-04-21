package com.spbsu.crawl;

import com.spbsu.commons.util.ThreadTools;
import com.spbsu.crawl.data.GameSession;
import com.spbsu.crawl.data.Message;
import com.spbsu.crawl.data.impl.InputModeMessage;
import com.spbsu.crawl.data.impl.KeyCode;
import com.spbsu.crawl.data.impl.KeyMessage;
import com.spbsu.crawl.data.impl.system.*;

import java.util.Collection;
import java.util.concurrent.BlockingQueue;
import java.util.function.Predicate;
import java.util.logging.Logger;

/**
 * User: qdeee
 * Date: 03.04.16
 */
public class GameProcess {
  private static final Logger LOG = Logger.getLogger(GameProcess.class.getName());
  public static final Predicate<Message> DUMP = message -> {
    System.out.println("messages: " + message);
    return false;
  };

  private final WSEndpoint endpoint;

  public GameProcess(WSEndpoint endpoint, GameSession session) {
    this.endpoint = endpoint;
  }

  public void start() {
    initGame();

    interact(new KeyMessage(KeyCode.LEFT), DUMP);
    interact(new KeyMessage(KeyCode.RIGHT), DUMP);
    interact(new KeyMessage(KeyCode.RIGHT), DUMP);

//    while (true) {
//      ThreadTools.sleep(1000);
//    }
  }

  private void interact(final Message userMessage, final Predicate<Message> handler) {
    endpoint.send(userMessage);
    final BlockingQueue<Message> messagesQueue = endpoint.getMessagesQueue();

    Message message;
    try {
      do {
        message = messagesQueue.take();
      }
      while (InputModeMessage.isStartMessage(message) ||
              message instanceof IgnoreMessage ||
              !handler.test(message) && !InputModeMessage.isEndMessage(message));
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
  }


  private void initGame() {
    endpoint.send(new LoginMessage("asd", "asd"));
    endpoint.send(new GoLobbyMessage());
    ThreadTools.sleep(1000);
    skipMessages();

    interact(new StartGameMessage("dcss-web-trunk"),  message -> message instanceof GameStarted);

//    if (message instanceof PlayerInfoMessage) {
//      final PlayerInfoMessage playerInfo = (PlayerInfoMessage) message;
//      System.out.println("Found player: " + playerInfo.title());
//    }
//    return false;
//    final PlayerInfoMessage playerInfo = findMessage(message, PlayerInfoMessage.class);
//    final UpdateMapMessage updateMapMessage = findMessage(message, UpdateMapMessage.class);
    //perform update initial models
  }

  private <T extends Message> T findMessage(final Collection<Message> messages, final Class<T> cls) {
    return messages.stream()
            .filter(cls::isInstance)
            .map(cls::cast)
            .findFirst()
            .orElseThrow(() -> new RuntimeException(cls.getSimpleName() + " not found"));
  }

  private void skipMessages() {
    while (!endpoint.getMessagesQueue().isEmpty()) {
      final Message message = endpoint.getMessagesQueue().poll();
      LOG.info("skipped message: " + message.type());
    }
  }
}
