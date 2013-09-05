package com.spbsu.ml.loss;

import com.spbsu.commons.math.vectors.Vec;
import com.spbsu.commons.math.vectors.impl.ArrayVec;
import com.spbsu.ml.Oracle1;

import static java.lang.Math.exp;
import static java.lang.Math.log;

/**
 * We use probability representation = \frac{e^x}{e^x + 1}.
 * User: solar
 * Date: 21.12.2010
 * Time: 22:37:55
 */
public class MulticlassLogLikelyhood implements Oracle1 {
  private final Vec target;

  public MulticlassLogLikelyhood(Vec target) {
    this.target = target;
  }

  @Override
  public Vec gradient(Vec point) {
    Vec result = new ArrayVec(point.dim());
    for (int i = 0; i < point.dim(); i++) {
      double expX = exp(point.get(i));
      double pX = expX / (1 + expX);
      if (target.get(i) > 0) // positive example
        result.set(i, 1 - pX);
      else // negative
        result.set(i, -pX);
    }
    return result;
  }

  public double value(Vec point) {
    double result = 0;
    for (int i = 0; i < point.dim(); i++) {
      double expX = exp(point.get(i));
      double pX = expX / (1 + expX);
      if (target.get(i) > 0) // positive example
        result -= log(pX);
      else // negative
        result -= log(1 - pX);
    }

    return result;
  }
}
