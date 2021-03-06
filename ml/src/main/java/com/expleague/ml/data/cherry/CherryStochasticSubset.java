package com.expleague.ml.data.cherry;

import com.expleague.commons.func.AdditiveStatistics;
import com.expleague.commons.func.Factory;
import com.expleague.ml.BFGrid;
import com.expleague.ml.data.Aggregate;
import com.expleague.ml.data.impl.BinarizedDataSet;
import com.expleague.ml.data.impl.RankedDataSet;

import java.util.Arrays;

public class CherryStochasticSubset implements CherryPointsHolder {
  private final BinarizedDataSet bds;
  private final RankedDataSet ranksDs;
  private final Factory<AdditiveStatistics> factory;
  private int[] points;
  private double[] weights;
  private double[] currentLogOutside;
  private double[] logInside;
  private Aggregate outsideAggregate;

  private AdditiveStatistics inside;
  private int length;

  public CherryStochasticSubset(RankedDataSet ranksDs, BinarizedDataSet bds, Factory<AdditiveStatistics> factory, int[] points) {
    this.bds = bds;
    this.ranksDs = ranksDs;
    this.factory = factory;
    this.points = points;
    this.weights = new double[points.length];
    this.logInside = new double[points.length];
    this.currentLogOutside = new double[points.length];
    Arrays.fill(currentLogOutside, Double.NEGATIVE_INFINITY);
    this.length = points.length;
  }


  public void visitAll(final Aggregate.IntervalVisitor<? extends AdditiveStatistics> visitor) {
    outsideAggregate.visit(visitor);
  }

  @Override
  public BFGrid grid() {
    return bds.grid();
  }

  @Override
  public void endClause() {
  }

  @Override
  public void startClause() {
    inside = factory.create();
    {
      for (int i = 0; i < weights.length; ++i) {
        logInside[i] += Math.log(1 - Math.exp(currentLogOutside[i]));
        weights[i] = Math.exp(logInside[i]);
        currentLogOutside[i] = 0;
      }
    }
    this.outsideAggregate = new Aggregate(bds, factory, points, weights);
  }

  private void calcWeights() {
    for (int i = 0; i < weights.length; ++i) {
      weights[i]  = 1-Math.exp(logInside[i]) * (1 - Math.exp(currentLogOutside[i]));
    }
  }


  private AdditiveStatistics calcInside() {
    AdditiveStatistics stat = factory.create();
    for (int i = 0; i < points.length; ++i) {
      stat.append(points[i], 1 - weights[i]);
    }
    return stat;
  }

  final double alpha = 0.01;
  final double beta =   10;

  private double leftProb(double rk, double border)  {
    return Math.exp(Math.min(alpha * (rk - border- beta) , 0));
  }

  private double rightProb(double rk, double border) {
    return Math.exp(Math.min(alpha * (border - rk- beta) , 0));
  }


  private void updateLogProbs(final BFGrid.Row row,
                              final int startBin, final int endBin) {
    double leftRank = startBin > 0 ? row.bf(startBin - 1).power() : 0;
    double rightRank = endBin == row.size() ? bds.original().length() : row.bf(endBin).power();
    float[] rank = ranksDs.feature(row.findex());
    for (int i = 0; i < points.length; ++i) {
      float rk = rank[points[i]];
      final double pLeft = leftProb(rk, leftRank);
      final double pRight = rightProb(rk, rightRank);
      currentLogOutside[i] += Math.log(1 - pLeft * pRight);
    }
  }

  @Override
  public AdditiveStatistics addCondition(final BFGrid.Row row,
                                         final int startBin, final int endBin) {
    updateLogProbs(row, startBin, endBin);
    calcWeights();
    outsideAggregate = new Aggregate(bds, factory, points, weights);
    inside = calcInside();
    return inside;
  }


  public AdditiveStatistics total() {
    return outsideAggregate.total().append(inside);
  }

  public AdditiveStatistics inside() {
    return factory.create().append(inside);
  }

  public AdditiveStatistics outside() {
    return outsideAggregate.total();
  }


}
