package com.spbsu.ml.methods;

import com.spbsu.commons.math.vectors.Vec;
import com.spbsu.commons.math.vectors.VecTools;
import com.spbsu.commons.math.vectors.impl.ArrayVec;
import com.spbsu.commons.util.ArrayTools;
import com.spbsu.ml.FuncC1;
import com.spbsu.ml.func.FuncEnsemble;
import com.spbsu.ml.optimization.Optimize;
import com.sun.javaws.exceptions.InvalidArgumentException;
import gnu.trove.list.TIntList;
import gnu.trove.list.array.TIntArrayList;

import java.util.Random;

/**
 * User: qdeee
 * Date: 17.03.14
 */
public class StochasticGradientDescent implements Optimize<FuncEnsemble> {
  private final int iterations;
  private final double step;
  private Random rand;

  public StochasticGradientDescent(final int iterations, final double step, Random rand) {
    this.iterations = iterations;
    this.step = step;
    this.rand = rand;
  }

  @Override
  public Vec optimize(final FuncEnsemble sumFuncs) {
    if (sumFuncs.last().gradient() == null) {
      throw new IllegalArgumentException("Internal functions must implement not-null gradient()");
    }

    Vec x = new ArrayVec(sumFuncs.xdim());
    for (int i = 0; i < x.dim(); i++) {
      x.set(i, rand.nextGaussian());
    }

    int iter = 0;
    TIntList indices = new TIntArrayList(ArrayTools.sequence(0, sumFuncs.size()));
    while (iter++ < iterations) {
      indices.shuffle(rand);
      for (int i = 0; i < indices.size(); i++) {
        VecTools.incscale(x, sumFuncs.models[i].gradient().trans(x), -step);
      }
    }
    return x;
  }
}
