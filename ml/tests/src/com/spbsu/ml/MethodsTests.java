package com.spbsu.ml;

import com.spbsu.commons.func.Action;
import com.spbsu.commons.func.Computable;
import com.spbsu.commons.math.vectors.Mx;
import com.spbsu.commons.math.vectors.Vec;
import com.spbsu.commons.math.vectors.VecTools;
import com.spbsu.commons.math.vectors.impl.ArrayVec;
import com.spbsu.commons.math.vectors.impl.VecArrayMx;
import com.spbsu.commons.math.vectors.impl.VecBasedMx;
import com.spbsu.commons.random.FastRandom;
import com.spbsu.ml.data.DSIterator;
import com.spbsu.ml.data.DataSet;
import com.spbsu.ml.data.impl.DataSetImpl;
import com.spbsu.ml.func.Ensemble;
import com.spbsu.ml.func.NormalizedLinear;
import com.spbsu.ml.loss.L2;
import com.spbsu.ml.loss.LLLogit;
import com.spbsu.ml.loss.SatL2;
import com.spbsu.ml.loss.WeightedLoss;
import com.spbsu.ml.methods.*;
import com.spbsu.ml.methods.trees.GreedyContinuesObliviousSoftBondariesRegressionTree;
import com.spbsu.ml.methods.trees.GreedyExponentialObliviousTree;
import com.spbsu.ml.methods.trees.GreedyObliviousTree;
import com.spbsu.ml.models.ContinousObliviousTree;
import com.spbsu.ml.models.ObliviousTree;
import com.spbsu.ml.models.ProbabilisticGraphicalModel;
import gnu.trove.TDoubleDoubleHashMap;
import gnu.trove.TDoubleIntHashMap;

import java.util.Random;

/**
 * User: solar
 * Date: 26.11.12
 * Time: 15:50
 */
public class MethodsTests extends GridTest {
  private FastRandom rng;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    rng = new FastRandom();
  }

  public void testPGMFit3x3() {
    ProbabilisticGraphicalModel original = new ProbabilisticGraphicalModel(new VecBasedMx(3, new ArrayVec(new double[]{
            0, 0.2, 0.8,
            0, 0, 1.,
            0, 0, 0
    })));

    Vec[] ds = new Vec[10000];
    for (int i = 0; i < ds.length; i++) {
      ds[i] = original.next(rng);
    }
    final DataSetImpl dataSet = new DataSetImpl(new VecArrayMx(ds), new ArrayVec(ds.length));
    final PGMEM pgmem = new PGMEM(new VecBasedMx(original.topology.columns(), VecTools.fill(new ArrayVec(original.topology.dim()), 1.)), 0.8, 100);
    final ProbabilisticGraphicalModel fit = pgmem.fit(dataSet, new LLLogit(VecTools.fill(new ArrayVec(dataSet.power()), 1.)));
    VecTools.fill(fit.topology.row(fit.topology.rows() - 1), 0);
    assertTrue(VecTools.distance(fit.topology, original.topology) < 0.01 * fit.topology.dim());
  }

  public void testPGMFit5x5() {
    ProbabilisticGraphicalModel original = new ProbabilisticGraphicalModel(new VecBasedMx(5, new ArrayVec(new double[]{
            0, 0.2, 0.3,  0.1,  0.4,
            0, 0,   0.25, 0.25, 0.5,
            0, 0,   0,    0.1,  0.9,
            0, 0,   0.5,  0,    0.5,
            0, 0,   0,    0,    0
    })));

    Vec[] ds = new Vec[10000];
    for (int i = 0; i < ds.length; i++) {
      ds[i] = original.next(rng);
    }
    final DataSetImpl dataSet = new DataSetImpl(new VecArrayMx(ds), new ArrayVec(ds.length));
    final PGMEM pgmem = new PGMEM(new VecBasedMx(original.topology.columns(), VecTools.fill(new ArrayVec(original.topology.dim()), 1.)), 0.8, 100);
    final ProbabilisticGraphicalModel fit = pgmem.fit(dataSet, new LLLogit(VecTools.fill(new ArrayVec(dataSet.power()), 1.)));
    VecTools.fill(fit.topology.row(fit.topology.rows() - 1), 0);
    assertTrue(VecTools.distance(fit.topology, original.topology) < 0.01 * fit.topology.dim());
  }

  public void testPGMFit10x10Rand() {
    final VecBasedMx originalMx = new VecBasedMx(10, new ArrayVec(100));
    for (int i = 0; i < originalMx.rows(); i++) {
      for (int j = 0; j < originalMx.columns(); j++)
        originalMx.set(i, j, rng.nextDouble() < 0.5 && j > 0 ? 1 : 0);
      VecTools.normalizeL1(originalMx.row(i));
    }
    VecTools.fill(originalMx.row(originalMx.rows() - 1), 0);
    ProbabilisticGraphicalModel original = new ProbabilisticGraphicalModel(originalMx);

    Vec[] ds = new Vec[10000];
    for (int i = 0; i < ds.length; i++) {
      ds[i] = original.next(rng);
    }
    final DataSetImpl dataSet = new DataSetImpl(new VecArrayMx(ds), new ArrayVec(ds.length));
    final PGMEM pgmem = new PGMEM(new VecBasedMx(original.topology.columns(), VecTools.fill(new ArrayVec(original.topology.dim()), 1.)), 0.8, 100);
    final ProbabilisticGraphicalModel fit = pgmem.fit(dataSet, new LLLogit(VecTools.fill(new ArrayVec(dataSet.power()), 1.)));
    VecTools.fill(fit.topology.row(fit.topology.rows() - 1), 0);
    assertTrue(VecTools.distance(fit.topology, original.topology) < 0.01 * fit.topology.dim());
  }

  public void testPGMFit() {


  }

  public void testLARS() {
    final LARSMethod lars = new LARSMethod();
//    lars.addListener(modelPrinter);
    final NormalizedLinear model = lars.fit(learn, new L2(learn.target()));
    System.out.println(new L2(validate.target()).value(model.transAll(validate.data())));
  }

  public void testGRBoost() {
    final GradientBoosting<L2> boosting = new GradientBoosting<L2>(new BootstrapOptimization<L2>(new GreedyRegion(new FastRandom(), GridTools.medianGrid(learn, 32)), rng),
        new Computable<Vec, L2>() {
          @Override
          public L2 compute(Vec argument) {
            return new L2(argument);
          }
        }, 10000, 0.02);
    final Action counter = new ProgressHandler() {
      int index = 0;

      @Override
      public void invoke(Trans partial) {
        System.out.print("\n" + index++);
      }
    };
    final ScoreCalcer learnListener = new ScoreCalcer("\tlearn:\t", learn);
    final ScoreCalcer validateListener = new ScoreCalcer("\ttest:\t", validate);
    final Action modelPrinter = new ModelPrinter();
    final Action qualityCalcer = new QualityCalcer();
    boosting.addListener(counter);
    boosting.addListener(learnListener);
    boosting.addListener(validateListener);
    boosting.addListener(qualityCalcer);
//    boosting.addListener(modelPrinter);
    boosting.fit(learn, new L2(learn.target()));
  }

  public void testGTDRBoost() {
    final GradientBoosting<L2> boosting = new GradientBoosting<L2>(new BootstrapOptimization<L2>(new GreedyTDRegion<WeightedLoss<L2>>(GridTools.medianGrid(learn, 32)), rng), 10000, 0.02);
    final Action counter = new ProgressHandler() {
      int index = 0;

      @Override
      public void invoke(Trans partial) {
        System.out.print("\n" + index++);
      }
    };
    final ScoreCalcer learnListener = new ScoreCalcer("\tlearn:\t", learn);
    final ScoreCalcer validateListener = new ScoreCalcer("\ttest:\t", validate);
    final Action modelPrinter = new ModelPrinter();
    final Action qualityCalcer = new QualityCalcer();
    boosting.addListener(counter);
    boosting.addListener(learnListener);
    boosting.addListener(validateListener);
    boosting.addListener(qualityCalcer);
//    boosting.addListener(modelPrinter);
    boosting.fit(learn, new L2(learn.target()));
  }

  public void testOTBoost() {
    final GradientBoosting<L2> boosting = new GradientBoosting<L2>(new BootstrapOptimization<L2>(new GreedyObliviousTree(GridTools.medianGrid(learn, 32), 6), rng), 2000, 0.01);
    final Action counter = new ProgressHandler() {
      int index = 0;

      @Override
      public void invoke(Trans partial) {
        System.out.print("\n" + index++);
      }
    };
    final ScoreCalcer learnListener = new ScoreCalcer(/*"\tlearn:\t"*/"\t", learn);
    final ScoreCalcer validateListener = new ScoreCalcer(/*"\ttest:\t"*/"\t", validate);
    final Action modelPrinter = new ModelPrinter();
    final Action qualityCalcer = new QualityCalcer();
    boosting.addListener(counter);
    boosting.addListener(learnListener);
    boosting.addListener(validateListener);
    //boosting.addListener(qualityCalcer);
//    boosting.addListener(modelPrinter);
    boosting.fit(learn, new SatL2(learn.target()));
  }

  public void testCOTBoost() {
    final GradientBoosting<L2> boosting = new GradientBoosting<L2>(
            new GreedyContinuesObliviousSoftBondariesRegressionTree(new FastRandom(), learn, GridTools.medianGrid(learn, 32),6, 10, true, 1, 0, 0, 1e5),
            2000, 0.01);
    final Action counter = new Action<Trans>() {
      int index = 0;

      @Override
      public void invoke(Trans partial) {
        System.out.print("\n" + index++);
      }
    };
    final ScoreCalcer learnListener = new ScoreCalcer(/*"\tlearn:\t"*/"\t", learn);
    final ScoreCalcer validateListener = new ScoreCalcer(/*"\ttest:\t"*/"\t", validate);
    final Action modelPrinter = new ModelPrinter();
    final Action qualityCalcer = new QualityCalcer();
    boosting.addListener(counter);
    boosting.addListener(learnListener);
    boosting.addListener(validateListener);
    //boosting.addListener(qualityCalcer);
//    boosting.addListener(modelPrinter);
    boosting.fit(learn, new SatL2(learn.target()));
  }

  public void testEOTBoost() {
    final GradientBoosting<SatL2> boosting = new GradientBoosting<SatL2>(
        new BootstrapOptimization<L2>(
            new GreedyExponentialObliviousTree(
                new FastRandom(),
                learn,
                GridTools.medianGrid(learn, 32), 6), rng),
        2000, 0.005);
    final Action counter = new Action<Trans>() {
      int index = 0;

      @Override
      public void invoke(Trans partial) {
        System.out.print("\n" + index++);
      }
    };
    final ScoreCalcer learnListener = new ScoreCalcer(/*"\tlearn:\t"*/"\t", learn);
    final ScoreCalcer validateListener = new ScoreCalcer(/*"\ttest:\t"*/"\t", validate);
    final Action modelPrinter = new ModelPrinter();
    final Action qualityCalcer = new QualityCalcer();
    boosting.addListener(counter);
    boosting.addListener(learnListener);
    boosting.addListener(validateListener);
    //boosting.addListener(qualityCalcer);
//    boosting.addListener(modelPrinter);
    boosting.fit(learn, new SatL2(learn.target()));
  }

  private double sqr(double x) {
    return x * x;
  }

  public void testObliviousTree() {
    ScoreCalcer scoreCalcerValidate = new ScoreCalcer(" On validate data Set loss = ", validate);
    ScoreCalcer scoreCalcerLearn = new ScoreCalcer(" On learn data Set loss = ", learn);
    for (int depth = 1; depth <= 6; depth++) {
      ObliviousTree tree = (ObliviousTree) new GreedyObliviousTree(GridTools.medianGrid(learn, 32), depth).fit(learn, new L2(learn.target()));
      System.out.print("Oblivious Tree depth = " + depth);
      scoreCalcerLearn.invoke(tree);
      scoreCalcerValidate.invoke(tree);
      System.out.println();
    }
  }

  public void testContinousObliviousTree() {
    ScoreCalcer scoreCalcerValidate = new ScoreCalcer(/*" On validate data Set loss = "*/"\t", validate);
    ScoreCalcer scoreCalcerLearn = new ScoreCalcer(/*"On learn data Set loss = "*/"\t", learn);
    for (int depth = 1; depth <= 6; depth++) {
      ContinousObliviousTree tree = new GreedyContinuesObliviousSoftBondariesRegressionTree(
          new FastRandom(),
          learn,
          GridTools.medianGrid(learn, 32), depth, 10, true, 1, 0.1, 1, 1e5).fit(learn, new L2(learn.target()));
      //for(int i = 0; i < 10/*learn.target().ydim()*/;i++)
      // System.out.println(learn.target().get(i) + "= " + tree.value(learn.data().row(i)));
      System.out.print("Oblivious Tree deapth = " + depth);
      scoreCalcerLearn.invoke(tree);
      scoreCalcerValidate.invoke(tree);

      System.out.println();
      //System.out.println(tree.toString());
    }
  }

  public void testExponentialObliviousTree() {
    ScoreCalcer scoreCalcerValidate = new ScoreCalcer(/*" On validate data Set loss = "*/"\t", validate);
    ScoreCalcer scoreCalcerLearn = new ScoreCalcer(/*"On learn data Set loss = "*/"\t", learn);
    for (int depth = 1; depth <= 6; depth++) {
      ContinousObliviousTree tree = new GreedyExponentialObliviousTree(
          new FastRandom(),
          learn,
          GridTools.medianGrid(learn, 32), depth).fit(learn, new L2(learn.target()));
      //for(int i = 0; i < 10/*learn.target().ydim()*/;i++)
      // System.out.println(learn.target().get(i) + "= " + tree.value(learn.data().row(i)));
      System.out.print("Oblivious Tree deapth = " + depth);
      scoreCalcerLearn.invoke(tree);
      scoreCalcerValidate.invoke(tree);

      System.out.println();
      //System.out.println(tree.toString());
    }
  }

  //Not safe can make diffrent size for learn and test
  public Mx cutNonContinuesFeatures(Mx ds, boolean continues[]) {

    int continuesFeatures = 0;
    for (int j = 0; j < ds.columns(); j++)
      for (int i = 0; i < ds.rows(); i++)
        if ((Math.abs(ds.get(i, j)) > 1e-7) && (Math.abs(ds.get(i, j) - 1) > 1e-7)) {
          continues[j] = true;
          continuesFeatures++;
          break;
        }
    int reg[] = new int[ds.columns()];
    int cnt = 0;
    for (int i = 0; i < ds.columns(); i++)
      if (continues[i])
        reg[i] = cnt++;
    Mx data = new VecBasedMx(ds.rows(), continuesFeatures);
    for (int i = 0; i < ds.rows(); i++)
      for (int j = 0; j < ds.columns(); j++)
        if (continues[j])
          data.set(i, reg[j], ds.get(i, j));
    return data;
  }

  public boolean checkEigeDecomposion(Mx mx, Mx q, Mx sigma) {
    for (int i = 0; i < mx.rows(); i++) {
      for (int j = 0; j < mx.columns(); j++)
        if (Math.abs(VecTools.multiply(mx, q.row(i)).get(j) - q.get(i, j) * sigma.get(i, i)) > 1e-9) {
          System.out.println(VecTools.multiply(mx, q.row(i)).get(j));
          System.out.println(q.get(i, j) * sigma.get(i, i));
          return false;
        }
    }
    return true;
  }

  public void doPCA(DataSet[] mas) {
    boolean continues[] = new boolean[learn.xdim()];
    Mx learnMx = cutNonContinuesFeatures(learn.data(), continues);
    Mx validateMx = cutNonContinuesFeatures(validate.data(), continues);
    //System.out.println(learnMx);
    Mx mx = VecTools.multiply(VecTools.transpose(learnMx), learnMx);
    Mx q = new VecBasedMx(mx.columns(), mx.rows());
    Mx sigma = new VecBasedMx(mx.columns(), mx.rows());
    VecTools.eigenDecomposition(mx, q, sigma);
    assertTrue(checkEigeDecomposion(mx, q, sigma));
    //System.exit(0);
    //System.out.println(mx);
    //System.out.println(q);
    learnMx = VecTools.multiply(learnMx, VecTools.transpose(q));
    validateMx = VecTools.multiply(validateMx, q);
    int reg[] = new int[learn.xdim()], cnt = 0;
    for (int i = 0; i < learn.xdim(); i++)
      if (!continues[i])
        reg[i] = cnt++;
    //System.out.println(q);
    //System.exit(-1);
    Mx learnOut = new VecBasedMx(learn.power(), learn.xdim());
    Mx validateOut = new VecBasedMx(validate.power(), validate.xdim());
    for (int i = 0; i < learn.power(); i++) {
      for (int j = 0; j < learnMx.columns(); j++)
        learnOut.set(i, j, learnMx.get(i, j));
      for (int j = 0; j < learn.xdim(); j++)
        if (!continues[j])
          learnOut.set(i, reg[j] + learnMx.columns(), learn.data().get(i, j));
    }
    System.out.println(learnOut);
    System.exit(0);
    for (int i = 0; i < validate.power(); i++) {
      for (int j = 0; j < validateMx.columns(); j++)
        validateOut.set(i, j, validateMx.get(i, j));
      for (int j = 0; j < validate.xdim(); j++)
        if (!continues[j])
          validateOut.set(i, reg[j] + validateMx.columns(), validate.data().get(i, j));
    }
    mas[0] = new DataSetImpl(learnOut, learn.target());
    mas[1] = new DataSetImpl(validateOut, validate.target());


  }

  //Bad Idea
  public void testPSAContinousObliviousTree() {
    DataSet mas[] = new DataSet[2];
    doPCA(mas);
    DataSet myValidate = mas[1], myLearn = mas[0];
    System.out.println(myLearn.data().row(0));
    System.out.println(learn.data().row(0));
    ScoreCalcer scoreCalcerValidate = new ScoreCalcer(/*" On validate data Set loss = "*/"\t", myValidate);
    ScoreCalcer scoreCalcerLearn = new ScoreCalcer(/*"On learn data Set loss = "*/"\t", myLearn);
    //System.out.println(learn.data());
    for (int depth = 1; depth <= 6; depth++) {
      ContinousObliviousTree tree = new GreedyContinuesObliviousSoftBondariesRegressionTree(new FastRandom(), myLearn, GridTools.medianGrid(myLearn, 32), depth, 1, true, 1, 0.1, 1, 1e5).fit(myLearn, new L2(myLearn.target()));
      //for(int i = 0; i < 10/*learn.target().ydim()*/;i++)
      // System.out.println(learn.target().get(i) + "= " + tree.value(learn.data().row(i)));
      System.out.print("Oblivious Tree deapth = " + depth);
      scoreCalcerLearn.invoke(tree);
      scoreCalcerValidate.invoke(tree);

      System.out.println();
      //System.out.println(tree.toString());
    }
  }

  public void testPSACOTboost() {
    DataSet mas[] = new DataSet[2];
    doPCA(mas);
    DataSet myValidate = mas[1], myLearn = mas[0];
        /*for(int i = 0;i < myLearn.xdim();i++){
            double mx = -1e10,mn = 1e10;
            for(int j = 0; j < myLearn.power();j++)        {
                mx = Math.max(mx, myLearn.data().get(j,i));
                mn = Math.min(mn, myLearn.data().get(j, i));

            }
            System.out.println(mn + "->"  + mx);
        } */


    final GradientBoosting<L2> boosting = new GradientBoosting<L2>(
            new GreedyContinuesObliviousSoftBondariesRegressionTree(new FastRandom(), myLearn, GridTools.medianGrid(myLearn, 32), 6, 6, true, 1, 0.1, 1, 1e6),
            2000, 0.01);
    final Action counter = new ProgressHandler() {
      int index = 0;

      @Override
      public void invoke(Trans partial) {
        System.out.print("\n" + index++);
      }
    };
    final ScoreCalcer learnListener = new ScoreCalcer(/*"\tlearn:\t"*/"\t", myLearn);
    final ScoreCalcer validateListener = new ScoreCalcer(/*"\ttest:\t"*/"\t", myValidate);
    final Action modelPrinter = new ModelPrinter();
    final Action qualityCalcer = new QualityCalcer();
    boosting.addListener(counter);
    boosting.addListener(learnListener);
    boosting.addListener(validateListener);
    //boosting.addListener(qualityCalcer);
//    boosting.addListener(modelPrinter);
    boosting.fit(learn, new L2(learn.target()));
  }

  public void testDebugContinousObliviousTree() {
    //ScoreCalcer scoreCalcerValidate = new ScoreCalcer(" On validate data Set loss = ", validate);
    double[] data = {0, 1, 2};
    double[] target = {0, 1, 2};

    DataSet debug = new DataSetImpl(data, target);
    ScoreCalcer scoreCalcerLearn = new ScoreCalcer(" On learn data Set loss = ", debug);
    for (int depth = 1; depth <= 1; depth++) {
      ContinousObliviousTree tree = new GreedyContinuesObliviousSoftBondariesRegressionTree(new FastRandom(), debug, GridTools.medianGrid(debug, 32), depth, 1, true, 10, 0.1, 1, 1e5).fit(debug, new L2(debug.target()));
      System.out.print("Oblivious Tree deapth = " + depth);
      scoreCalcerLearn.invoke(tree);
      System.out.println(tree.toString());
      System.out.println();
    }
  }

  private static class ScoreCalcer implements ProgressHandler {
    final String message;
    final Vec current;
    private final DataSet ds;

    public ScoreCalcer(String message, DataSet ds) {
      this.message = message;
      this.ds = ds;
      current = new ArrayVec(ds.power());
    }

    double min = 1e10;

    @Override
    public void invoke(Trans partial) {
      if (partial instanceof Ensemble) {
        final Ensemble linear = (Ensemble) partial;
        final Trans increment = linear.last();
        final DSIterator iter = ds.iterator();
        int index = 0;
        while (iter.advance()) {
          current.adjust(index++, linear.wlast() * ((Func) increment).value(iter.x()));
        }
      } else {
        final DSIterator iter = ds.iterator();
        int index = 0;
        while (iter.advance()) {
          current.set(index++, ((Func) partial).value(iter.x()));
        }
      }
      double curLoss = VecTools.distance(current, ds.target()) / Math.sqrt(ds.power());
      System.out.print(message + curLoss);
      min = Math.min(curLoss, min);
      System.out.print(" minimum = " + min);
    }
  }

  private static class ModelPrinter implements ProgressHandler {
    @Override
    public void invoke(Trans partial) {
      if (partial instanceof Ensemble) {
        final Ensemble model = (Ensemble) partial;
        final Trans increment = model.last();
        System.out.print("\t" + increment);
      }
    }
  }

  private class QualityCalcer implements ProgressHandler {
    Vec residues = VecTools.copy(learn.target());
    double total = 0;
    int index = 0;

    @Override
    public void invoke(Trans partial) {
      if (partial instanceof Ensemble) {
        final Ensemble model = (Ensemble) partial;
        final Trans increment = model.last();

        final DSIterator iterator = learn.iterator();
        final TDoubleIntHashMap values = new TDoubleIntHashMap();
        final TDoubleDoubleHashMap dispersionDiff = new TDoubleDoubleHashMap();
        int index = 0;
        while (iterator.advance()) {
          final double value = ((Func) increment).value(iterator.x());
          values.adjustOrPutValue(value, 1, 1);
          final double ddiff = sqr(residues.get(index)) - sqr(residues.get(index) - value);
          residues.adjust(index, -model.wlast() * value);
          dispersionDiff.adjustOrPutValue(value, ddiff, ddiff);
          index++;
        }
//          double totalDispersion = VecTools.multiply(residues, residues);
        double score = 0;
        for (double key : values.keys()) {
          final double regularizer = 1 - 2 * Math.log(2) / Math.log(values.get(key) + 1);
          score += dispersionDiff.get(key) * regularizer;
        }
//          score /= totalDispersion;
        total += score;
        this.index++;
        System.out.print("\tscore:\t" + score + "\tmean:\t" + (total / this.index));
      }
    }
  }

  public void testDGraph() {
    Random rng = new FastRandom();
    for (int n = 1; n < 100; n++) {
      System.out.print("" + n);
      double d = 0;
      for (int t = 0; t < 100000; t++) {
        double sum = 0;
        double sum2 = 0;
        for (int i = 0; i < n; i++) {
          double v = learn.target().get(rng.nextInt(learn.power()));
          sum += v;
          sum2 += v * v;
        }
        d += (sum2 - sum * sum / n) / n;
      }
      System.out.println("\t" + d / 100000);
    }
  }
}


