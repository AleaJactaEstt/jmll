package com.spbsu.ml.methods;

import com.spbsu.commons.math.vectors.Mx;
import com.spbsu.commons.math.vectors.Vec;
import com.spbsu.commons.math.vectors.impl.ArrayVec;
import com.spbsu.commons.util.ArrayTools;
import com.spbsu.ml.BFGrid;
import com.spbsu.ml.Model;
import com.spbsu.ml.Oracle1;
import com.spbsu.ml.data.DataSet;
import com.spbsu.ml.data.Histogram;
import com.spbsu.ml.data.impl.BinarizedDataSet;
import com.spbsu.ml.data.impl.Bootstrap;
import com.spbsu.ml.loss.L2Loss;
import com.spbsu.ml.models.Region;
import gnu.trove.TIntArrayList;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import static java.lang.Math.pow;

/**
 * User: solar
 * Date: 15.11.12
 * Time: 15:19
 */
public class GreedyTDRegion implements MLMethodOrder1 {
  private final Random rng;
  protected final BFGrid grid;
  protected final BinarizedDataSet bds;
  private double alpha = 0.3;
  private double betta = 0.1;

  public GreedyTDRegion(Random rng, DataSet ds, BFGrid grid) {
    this(rng, ds, grid, 0.3, 0.1);
  }

  public GreedyTDRegion(Random rng, DataSet ds, BFGrid grid, double alpha, double betta) {
    this.rng = rng;
    this.grid = grid;
    this.alpha = alpha;
    this.betta = betta;
    bds = new BinarizedDataSet(ds, grid);
  }

  @Override
  public Model fit(DataSet learn, Oracle1 loss) {
    return fit(learn, loss, new ArrayVec(learn.power()));
  }

  public Model fit(DataSet learn, Oracle1 loss, Vec start) {
    assert loss.getClass() == L2Loss.class;
//    learn = learn instanceof Bootstrap ? ((Bootstrap) learn).original() : learn;
    final List<BFGrid.BinaryFeature> conditions = new ArrayList<BFGrid.BinaryFeature>(grid.size());
    final boolean[] conditionMasks = new boolean[grid.size()];
    final Mx data = learn instanceof Bootstrap ? ((Bootstrap) learn).original().data() : learn.data();
    final Vec target = learn instanceof Bootstrap ? ((Bootstrap) learn).original().target() : learn.target();
    int[] indices = learn instanceof Bootstrap ? ((Bootstrap) learn).order() : ArrayTools.sequence(0, learn.power());
    double[] scores = new double[grid.size()];
    final boolean[] masks = new boolean[grid.size()];
    TIntArrayList inducedIndices = new TIntArrayList(indices.length);

    double total = 0; //VecTools.sum(learn.target());
    double total2 = 0; //VecTools.sum2(learn.target());
    for (int i = 0; i < indices.length; i++) {
      final double y = target.get(indices[i]);
      total += y;
      total2 += y * y;
    }
    double totalWeight = indices.length;
    double currentScore = Double.MAX_VALUE;

    while(true) {
      final Histogram result = new Histogram(grid, target, start);
      bds.aggregate(result, indices);
      final Histogram histogram = result;
      final int complexity = conditions.size() + 1;
      final double ftotal = total;
      final double ftotal2 = total2;
      final double ftotalWeight = totalWeight;
      Arrays.fill(scores, 0.);

      histogram.score(scores, new Histogram.Judge() {
        @Override
        public double score(double sum, double sum2, double weight, int bf) {
          double lScore = GreedyTDRegion.this.score(weight, sum, sum2, complexity);
          double rScore = GreedyTDRegion.this.score(ftotalWeight - weight, ftotal - sum, ftotal2 - sum2, complexity);
          masks[bf] = lScore > rScore;
          return Math.min(lScore, rScore);
        }
      });
      final int bestBFIndex = ArrayTools.min(scores);
      if (bestBFIndex < 0)
        throw new RuntimeException("Can not find optimal split!");
      BFGrid.BinaryFeature bestBF = grid.bf(bestBFIndex);
      boolean isRight = masks[bestBFIndex];
      if (scores[bestBFIndex] >= currentScore)
        break;

      byte[] bins = bds.bins(bestBF.findex);
      double totalReduce = 0;
      double total2Reduce = 0;
      inducedIndices.clear();

      for (int t = 0; t < indices.length; t++) {
        final int index = indices[t];
//        if (!((bins[index] > bestBF.binNo) ^ isRight)) {
        double diff = isRight ? bins[index] - bestBF.binNo - 1 : bestBF.binNo - bins[index];
        if (rng.nextDouble() < pow(0.5, -diff)) {
          inducedIndices.add(index);
        }
        else {
          final double v = target.get(index);
          totalReduce += v;
          total2Reduce += v * v;
        }
      }
      if (inducedIndices.isEmpty() || inducedIndices.size() == indices.length)
        break;
      total -= totalReduce;
      total2 -= total2Reduce;
      conditionMasks[conditions.size()] = isRight;
      conditions.add(bestBF);
//      {
//        boolean[] tmpMask = new boolean[learn.power()];
//        for (int t = 0; t < inducedIndices.size(); t++) {
//          tmpMask[inducedIndices.get(t)] = true;
//        }
//        final Region region = new Region(conditions, conditionMasks, total / indices.length, indices.length, currentScore);
//        for (int t = 0; t < indices.length; t++) {
//          final int index = indices[t];
//          final Vec point = data.row(index);
//          if (region.contains(point) ^ tmpMask[index])
//            System.out.println(region.contains(point));
//        }
//      }

      indices = inducedIndices.toNativeArray();
      totalWeight = indices.length;
      currentScore = scores[bestBFIndex];
    }

    return new Region(conditions, conditionMasks, total/indices.length, indices.length, currentScore);
  }

  public double score(double n, double sum, double sum2, int complexity) {
    if (n <= 2)
      return Double.POSITIVE_INFINITY;
//    final double err = sum2 - sum * sum / n;
//    return (err - sum2);// * pow(0.9, complexity);
//    return (err * n * n / (n - 1) / (n - 1) - sum2);// * pow(0.99, complexity);
    return (- sum * sum * n / (n - 1) / (n - 1));// * pow(0.99, complexity);
//    return -sum * sum / log(n+2);
//    final double mean = sum/n;
//    final double D = sum2/n - mean * mean;
//    final double dD = (sum2 - (2 * (n - 1) * (n - 1) - 1) * mean * mean) / (n-1)/(n-1);
//    final double dD = (sum2 * (3*n - n*n - 1) - sum * sum/n)/n/(n-1)/(n-1);
//    System.out.println(n + " " + D + " " + dD + " " + (sqrt(sum2/n) - (sqrt(D) + sqrt(-dD))));
//    return n * ((sqrt(D) + sqrt(-dD)) - sqrt(sum2/n));
  }
}
