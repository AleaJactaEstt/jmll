package com.expleague.bernulli;

import com.expleague.commons.func.Factory;
import com.expleague.commons.util.BestHolder;
import com.expleague.commons.util.ThreadTools;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadPoolExecutor;

public class StochasticSearch<Model> {
  private static final ThreadPoolExecutor exec = ThreadTools.createBGExecutor("Search thread", -1);

  final Factory<Learner<Model>> factory;

  public StochasticSearch(Factory<Learner<Model>> factory) {
    this.factory = factory;
  }

  public Model fit(int tries) {
    final CountDownLatch latch = new CountDownLatch(tries);
    final BestHolder<Model> bestHolder = new BestHolder<>();
    for (int i = 0; i < tries; ++i) {
      exec.submit(new Runnable() {
        @Override
        public void run() {
          final Learner<Model> learner = factory.create();
          final FittedModel<Model> result = learner.fit();
          bestHolder.update(result.model, result.likelihood);
          latch.countDown();
        }
      });
    }
    try {
      latch.await();
    } catch (InterruptedException e) {
      //
    }
    return bestHolder.getValue();
  }
}
