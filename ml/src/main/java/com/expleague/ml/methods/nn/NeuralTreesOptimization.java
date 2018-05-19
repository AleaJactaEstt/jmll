package com.expleague.ml.methods.nn;

import com.expleague.commons.math.FuncC1;
import com.expleague.commons.math.Trans;
import com.expleague.commons.math.vectors.Mx;
import com.expleague.commons.math.vectors.Vec;
import com.expleague.commons.math.vectors.VecTools;
import com.expleague.commons.math.vectors.impl.mx.VecBasedMx;
import com.expleague.commons.math.vectors.impl.vectors.ArrayVec;
import com.expleague.commons.random.FastRandom;
import com.expleague.ml.BFGrid;
import com.expleague.ml.GridTools;
import com.expleague.ml.ProgressHandler;
import com.expleague.ml.data.set.VecDataSet;
import com.expleague.ml.data.set.impl.VecDataSetImpl;
import com.expleague.ml.factorization.impl.ALS;
import com.expleague.ml.func.Ensemble;
import com.expleague.ml.func.ScaledVectorFunc;
import com.expleague.ml.loss.L2Reg;
import com.expleague.ml.loss.WeightedLoss;
import com.expleague.ml.loss.blockwise.BlockwiseMLLLogit;
import com.expleague.ml.methods.BootstrapOptimization;
import com.expleague.ml.methods.GradientBoosting;
import com.expleague.ml.methods.Optimization;
import com.expleague.ml.methods.greedyRegion.GreedyProbLinearRegion;
import com.expleague.ml.methods.greedyRegion.GreedyProbLinearRegion.ProbRegion;
import com.expleague.ml.methods.multiclass.gradfac.GradFacMulticlass;
import com.expleague.ml.models.nn.ConvNet;

import java.io.PrintStream;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.IntStream;

public class NeuralTreesOptimization implements Optimization<BlockwiseMLLLogit, VecDataSet, Vec> {
  private final int numIterations;
  private final int nSampleBuildTree;
  private final ConvNet nn;
  private final FastRandom rng;
  private final int sgdIterations;
  private final int batchSize;
  private final PrintStream debug;
  private final double sgdStep;

  public NeuralTreesOptimization(int numIterations, int nSampleBuildTree, int sgdIterations, ConvNet nn, FastRandom rng) {
    this(numIterations, nSampleBuildTree, sgdIterations, nn, rng, System.out);
  }

  public NeuralTreesOptimization(int numIterations, int nSampleBuildTree, int sgdIterations, ConvNet nn, FastRandom rng, PrintStream debug) {
    this.numIterations = numIterations;
    this.nSampleBuildTree = 1000;//nSampleBuildTree;
    this.sgdIterations = 1000;//10;
    this.batchSize = 128;
    this.nn = nn;
    this.rng = rng;
    this.debug = debug;
    this.sgdStep = 1e-3;
  }

  @Override
  public Function<Vec, Vec> fit(VecDataSet learn, BlockwiseMLLLogit loss) {
    for (int iter = 0; iter < numIterations; iter++) {
      final HighLevelDataset highLearn = HighLevelDataset.sampleFromDataset(learn, loss, nn, nSampleBuildTree, rng);
      final HighLevelDataset highTest =
          HighLevelDataset.sampleFromDataset(learn, highLearn.getNormalizer(), loss, nn, nSampleBuildTree, rng);

      final Ensemble ensemble = fitBoosting(highLearn, highTest);

      {
        final Vec prevGrad = new ArrayVec(nn.wdim());
        for (int sgdIter = 0; sgdIter < sgdIterations; sgdIter++) {
          final Vec grad = VecTools.fill(new ArrayVec(nn.wdim()), 0);

          final Vec partial = new ArrayVec(nn.wdim());
          for (int i = 0; i < batchSize; i++) {
            final int sampleIdx = rng.nextInt(nSampleBuildTree);
            final Vec nnResult = highLearn.get(sampleIdx);
            final Vec treeGrad = ensembleGradient(ensemble, highLearn.loss(), nnResult, sampleIdx);
            final Vec baseVec = highLearn.baseVecById(sampleIdx);
            nn.gradientTo(baseVec, new TargetByTreeOut(treeGrad), partial);

            VecTools.append(grad, partial);
          }

          // FIXME
          final int lastLayerWStart = nn.wdim() - 500 * 80;
          for (int i = 0; i < lastLayerWStart; i++) {
            grad.set(i, 0);
          }
          //

          VecTools.scale(grad, sgdStep);
          VecTools.append(nn.weights(), grad);

          highLearn.update();
          highTest.update();

          final Mx resultTrain = ensemble.transAll(highLearn.data());
          final Mx resultTest = ensemble.transAll(highTest.data());
          final double lTrain = highLearn.loss().value(resultTrain);
          final double lTest = highTest.loss().value(resultTest);
          debug.println("sgd [" + (sgdIter) + "], loss(train): " + lTrain + " loss(test): " + lTest);
          debug.println("Grad alignment: " + VecTools.cosine(prevGrad, grad));
          VecTools.assign(prevGrad, grad);
        }
      }
    }

    final Vec xOpt = nn.weights();
    final HighLevelDataset allLearn = HighLevelDataset.createFromDs(learn, loss, nn);
    final Ensemble ensemble = fitBoosting(allLearn, allLearn);

    return argument -> {
      Vec result = nn.apply(argument, xOpt);
      return ensemble.trans(result);
    };
  }

  private Vec ensembleGradient(Ensemble ensemble, BlockwiseMLLLogit loss, Vec x, int blockId) {
    final Vec ensembleGrad = VecTools.fill(new ArrayVec(nn.ydim()), 0.);

    final Vec lossGrad = new ArrayVec(loss.blockSize());
    final Vec treeOut = ensemble.trans(x);
    loss.gradient(treeOut, lossGrad, blockId);

    final Vec currentWeights = new ArrayVec(loss.blockSize());
    final Vec grad = new ArrayVec(nn.ydim());
    for (int i = 0; i < ensemble.models.length; i++) {
      final ScaledVectorFunc model = (ScaledVectorFunc) ensemble.models[i];
      VecTools.assign(currentWeights, model.weights);
      VecTools.scale(currentWeights, lossGrad);
      ((ProbRegion) model.function).gradientTo(x, grad);
      VecTools.scale(grad, ensemble.weights.get(i) * VecTools.sum(currentWeights));
      VecTools.append(ensembleGrad, grad);
    }

    return ensembleGrad;
  }

  private Ensemble fitBoosting(HighLevelDataset learn, HighLevelDataset test) {
    final BFGrid grid = GridTools.medianGrid(learn.vec(), 32);
    final GreedyProbLinearRegion<WeightedLoss<L2Reg>> weak = new GreedyProbLinearRegion<>(grid, 6);
    final BootstrapOptimization bootstrap = new BootstrapOptimization(weak, rng);

    final GradientBoosting<BlockwiseMLLLogit> boosting = new GradientBoosting<>(new GradFacMulticlass(
        bootstrap, new ALS(150, 0.), L2Reg.class, false), L2Reg.class, 1000, 0.1);

    final Consumer<Trans> counter = new ProgressHandler() {
      int index = 0;

      @Override
      public void accept(Trans partial) {
        index++;

        if (index % 100 == 0) {
          final Mx resultTrain = partial.transAll(learn.data());
          final double lTrain = learn.loss().value(resultTrain);
          final Mx resultTest = partial.transAll(test.data());
          final double lTest = test.loss().value(resultTest);
          debug.println("boost [" + (index) + "], loss(train): " + lTrain + " loss(test): " + lTest);
        }
      }
    };
    boosting.addListener(counter);

    final Ensemble ensemble = boosting.fit(learn.vec(), learn.loss());

    final Vec result = ensemble.transAll(learn.data()).vec();
    final double curLossValue = learn.loss().value(result);
    debug.println("ensemble loss: " + curLossValue);

    return ensemble;
  }

  private static class HighLevelDataset {
    private final ConvNet nn;
    private final VecDataSet base;
    private final BlockwiseMLLLogit loss;
    private final int[] sampleIdxs;
    private final DataNormalizer normalizer;
    private Mx highData;


    private HighLevelDataset(Mx highData, DataNormalizer normalizer, ConvNet nn, VecDataSet base, BlockwiseMLLLogit loss, int[] sampleIdxs) {
      this.highData = highData;
      this.normalizer = normalizer;
      this.nn = nn;
      this.base = base;
      this.loss = loss;
      this.sampleIdxs = sampleIdxs;
    }

    static HighLevelDataset sampleFromDataset(VecDataSet ds, BlockwiseMLLLogit loss, ConvNet nn, int numSamples, FastRandom rng) {
      return sampleFromDataset(ds, null, loss, nn, numSamples, rng);
    }

    static HighLevelDataset sampleFromDataset(VecDataSet ds, DataNormalizer normalizer, BlockwiseMLLLogit loss, ConvNet nn, int numSamples, FastRandom rng) {
      Mx highData = new VecBasedMx(numSamples, nn.ydim());
      final int[] sampleIdx = new int[numSamples];

      for (int i = 0; i < numSamples; i++) {
        sampleIdx[i] = rng.nextInt(ds.length());
        final Vec result = nn.apply(ds.data().row(sampleIdx[i]));
        VecTools.assign(highData.row(i), result);
      }

      if (normalizer == null)
        normalizer = new DataNormalizer(highData, 0, 100.);
      highData = normalizer.transAll(highData, true);

      final Vec target = new ArrayVec(numSamples);
      IntStream.range(0, numSamples).forEach(idx -> target.set(idx, loss.label(sampleIdx[idx])));
      final BlockwiseMLLLogit newLoss = new BlockwiseMLLLogit(target, ds);

      return new HighLevelDataset(highData, normalizer, nn, ds, newLoss, sampleIdx);
    }

    static HighLevelDataset createFromDs(VecDataSet ds, BlockwiseMLLLogit loss, ConvNet nn) {
      Mx highData = new VecBasedMx(ds.length(), nn.ydim());
      for (int i = 0; i < ds.length(); i++) {
        final Vec result = nn.apply(ds.data().row(i));
        VecTools.assign(highData.row(i), result);
      }

      final DataNormalizer normalizer = new DataNormalizer(highData, 0., 100.);
      highData = normalizer.transAll(highData, true);

      return new HighLevelDataset(highData, normalizer, nn, ds, loss, IntStream.range(0, ds.length()).toArray());
    }

    public Mx data() {
      return highData;
    }

    public DataNormalizer getNormalizer() {
      return normalizer;
    }

    public BlockwiseMLLLogit loss() {
      return loss;
    }

    public VecDataSet vec() {
      return new VecDataSetImpl(highData, base);
    }

    public Vec baseVecById(int id) {
      return base.data().row(sampleIdxs[id]);
    }

    public void update() {
      for (int i = 0; i < highData.rows(); i++) {
        final Vec result = nn.apply(base.data().row(sampleIdxs[i]));
        VecTools.assign(highData.row(i), result);
      }
      highData = normalizer.transAll(highData, true);
    }

    public Vec get(int sampleIdx) {
      return highData.row(sampleIdx);
    }
  }

  private static class DataNormalizer extends Trans.Stub {
    private final Vec mean;
    private final Vec disp;
    private final double newMean;
    private final double newDisp;

    DataNormalizer(Mx data, double newMean, double newDisp) {
      this.newMean = newMean;
      this.newDisp = newDisp;
      final int featuresDim = data.columns();

      mean = VecTools.fill(new ArrayVec(featuresDim), 0.);
      disp = VecTools.fill(new ArrayVec(featuresDim), 0.);

      for (int i = 0; i < data.rows(); i++) {
        final Vec row = data.row(i);
        VecTools.append(mean, row);
        appendSqr(disp, row, 1.);
      }

      VecTools.scale(mean, 1. / data.rows());
      VecTools.scale(disp, 1. / data.rows());
      appendSqr(disp, mean, -1.);

      for (int i = 0; i < featuresDim; i++) {
        double v = disp.get(i) == 0. ? 1. : disp.get(i);
        disp.set(i, v);
      }
    }

    @Override
    public Vec transTo(Vec x, Vec to) {
      if (x.dim() != mean.dim()) {
        throw new IllegalArgumentException();
      }

      for (int i = 0; i < x.dim(); i++) {
        final double v = (x.get(i) - mean.get(i)) / Math.sqrt(disp.get(i)) * newDisp + newMean;
        to.set(i, v);
      }

      return to;
    }

    private void appendSqr(Vec to, Vec who, double alpha) {
      for (int i = 0; i < to.dim(); i++) {
        final double v = who.get(i);
        to.adjust(i, alpha * v * v);
      }
    }

    @Override
    public int xdim() {
      return mean.dim();
    }

    @Override
    public int ydim() {
      return xdim();
    }
  }

  private class TargetByTreeOut extends FuncC1.Stub {
    private final Vec treesGradient;

    TargetByTreeOut(Vec gradient) {
      this.treesGradient = gradient;
    }

    @Override
    public double value(Vec x) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Vec gradientTo(Vec x, Vec to) {
      VecTools.assign(to, treesGradient);
      return to;
    }

    @Override
    public int dim() {
      return treesGradient.dim();
    }
  }
}
