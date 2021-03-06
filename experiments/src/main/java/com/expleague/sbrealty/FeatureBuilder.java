package com.expleague.sbrealty;

import com.expleague.commons.func.Evaluator;
import com.expleague.commons.math.vectors.Vec;
import com.expleague.commons.math.vectors.impl.vectors.VecBuilder;
import com.expleague.ml.meta.DSItem;
import com.expleague.ml.meta.FeatureMeta;
import com.expleague.ml.meta.impl.JsonFeatureMeta;

import java.util.Date;
import java.util.List;
import java.util.function.Consumer;

/**
 * Experts League
 * Created by solar on 10.06.17.
 */
public class FeatureBuilder<T extends DSItem> implements Consumer<T> {
  private final FeatureMeta meta;
  private final VecBuilder builder = new VecBuilder();
  protected Evaluator<T> calc;

  protected FeatureBuilder(String id, String description, Evaluator<T> calc) {
    this.calc = calc;
    this.meta = FeatureMeta.create(id, description, FeatureMeta.ValueType.VEC);
  }

  public void init(List<Deal> deals, Vec signal, Date start, Date end) {
    builder.clear();
  }

  public FeatureMeta meta() {
    return meta;
  }

  public Vec build() {
    final Vec build = builder.build();
    builder.clear();
    return build;
  }

  @Override
  public void accept(T t) {
    builder.append(calc.value(t));
  }
}
