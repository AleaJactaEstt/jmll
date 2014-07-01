package com.spbsu.ml.data.tools;

import com.spbsu.commons.func.Computable;
import com.spbsu.commons.io.StreamTools;
import com.spbsu.commons.math.vectors.*;
import com.spbsu.commons.math.vectors.impl.basis.IntBasis;
import com.spbsu.commons.math.vectors.impl.basis.MxBasisImpl;
import com.spbsu.commons.math.vectors.impl.idxtrans.ArrayPermutation;
import com.spbsu.commons.math.vectors.impl.idxtrans.RowsPermutation;
import com.spbsu.commons.math.vectors.impl.mx.SparseMx;
import com.spbsu.commons.math.vectors.impl.mx.VecBasedMx;
import com.spbsu.commons.math.vectors.impl.vectors.ArrayVec;
import com.spbsu.commons.math.vectors.impl.vectors.IndexTransVec;
import com.spbsu.commons.math.vectors.impl.vectors.SparseVec;
import com.spbsu.commons.random.FastRandom;
import com.spbsu.commons.text.CharSequenceTools;
import com.spbsu.commons.util.Pair;
import com.spbsu.ml.BFGrid;
import com.spbsu.ml.CompositeTrans;
import com.spbsu.ml.Func;
import com.spbsu.ml.Trans;
import com.spbsu.ml.data.DSIterator;
import com.spbsu.ml.data.DataSet;
import com.spbsu.ml.data.impl.DataSetImpl;
import com.spbsu.ml.data.impl.DataSetImpl2;
import com.spbsu.ml.func.Ensemble;
import com.spbsu.ml.func.FuncJoin;
import com.spbsu.ml.func.TransJoin;
import com.spbsu.ml.io.ModelsSerializationRepository;
import com.spbsu.ml.loss.StatBasedLoss;
import com.spbsu.ml.loss.WeightedLoss;
import com.spbsu.ml.models.ObliviousMultiClassTree;
import com.spbsu.ml.models.ObliviousTree;
import gnu.trove.list.TIntList;
import gnu.trove.list.linked.TIntLinkedList;
import gnu.trove.map.hash.TIntObjectHashMap;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;

import java.io.*;
import java.lang.reflect.Constructor;
import java.util.*;
import java.util.zip.GZIPInputStream;

import static java.lang.Math.max;

/**
 * User: solar
 * Date: 26.12.10
 * Time: 19:05
 */
public class DataTools {
  private static final String COMMON_DELIMETER = " ";
  private static final String VEC_DELIMETER = ":";

  public static DataSet loadFromFeaturesTxt(String file) throws IOException {
    return loadFromFeaturesTxt(file.endsWith(".gz") ? new InputStreamReader(new GZIPInputStream(new FileInputStream(file))) : new FileReader(file), null);
  }

  public static DataSet loadFromFeaturesTxt(String file, TIntObjectHashMap<CharSequence> meta) throws IOException {
    return loadFromFeaturesTxt(file.endsWith(".gz") ? new InputStreamReader(new GZIPInputStream(new FileInputStream(file))) : new FileReader(file), meta);
  }

  public static DataSet loadFromFeaturesTxt(Reader reader) throws IOException {
    return loadFromFeaturesTxt(reader, null);
  }

  public static DataSet loadFromFeaturesTxt(Reader in, TIntObjectHashMap<CharSequence> meta) throws IOException {
    final LineNumberReader reader = new LineNumberReader(in);
    List<double[]> set = new LinkedList<double[]>();
    List<Double> targets = new LinkedList<Double>();
    int maxFeatures = 0;
    String line;
    final List<Double> featuresA = new ArrayList<Double>();
    int lindex = 0;
    while ((line = reader.readLine()) != null) {
      try {
        final StringBuffer metaline = new StringBuffer();
        featuresA.clear();
        StringTokenizer tok = new StringTokenizer(line, "\t");
        metaline.append(tok.nextToken()); // group
        targets.add(Double.parseDouble(tok.nextToken()));
        metaline.append("\t").append(tok.nextToken()); // item name
        metaline.append("\t").append(tok.nextToken()); // equality class inside group
        while (tok.hasMoreTokens()) {
          featuresA.add(Double.parseDouble(tok.nextToken()));
        }
        maxFeatures = Math.max(maxFeatures, featuresA.size());
        double[] features = new double[maxFeatures];
        for (int i = 0; i < featuresA.size(); i++) {
          features[i] = featuresA.get(i);
        }
        if (meta != null)
          meta.put(set.size(), metaline);
        set.add(features);
      }
      catch (Throwable th) {
        System.out.println("Failed to parse line " + lindex + ":");
        System.out.println(line);
      }
      lindex++;
    }
    double[] data = new double[maxFeatures * set.size()];
    double[] target = new double[set.size()];
    Iterator<double[]> iterF = set.iterator();
    Iterator<Double> iterT = targets.iterator();
    int featuresCount = maxFeatures;
    int index = 0;
    while (iterF.hasNext()) {
      final double[] features = iterF.next();
      System.arraycopy(features, 0, data, index * featuresCount, features.length);
      target[index] = iterT.next();
      index++;
    }
    return new DataSetImpl(data, target);
  }

  public static DataSet loadFromSparseFeaturesTxt(String file) throws IOException {
    return loadFromSparseFeaturesTxt(file.endsWith(".gz") ? new InputStreamReader(new GZIPInputStream(new FileInputStream(file))) : new FileReader(file));
  }

  public static DataSet loadFromSparseFeaturesTxt(final Reader in) throws IOException {
    final BufferedReader br = new BufferedReader(in);

    //read matrix sizes
    int cols = Integer.parseInt(br.readLine());
    int rows = Integer.parseInt(br.readLine());

    //create matrix with given params
    final SparseMx<MxBasisImpl> mx = new SparseMx<MxBasisImpl>(new MxBasisImpl(rows, cols));
    final double[] targets = new double[rows];
    final IntBasis rowBias = new IntBasis(cols);

    //read string like 'target1 feature_id1:val1 feature_id4:val4 ...'
    String inline;
    int curRow = 0;
    while ((inline = br.readLine()) != null) {
      final StringTokenizer tok = new StringTokenizer(inline, COMMON_DELIMETER);
      targets[curRow] = Double.parseDouble(tok.nextToken());
      final SparseVec<IntBasis> vec = new SparseVec<IntBasis>(rowBias);
      while (tok.hasMoreElements()) {
        final String[] parts = tok.nextToken().split(VEC_DELIMETER);
        vec.add(Integer.parseInt(parts[0]), Double.parseDouble(parts[1]));
      }
      mx.setRow(curRow, vec);
      ++curRow;
    }
    //don't use DataSetImpl it's iterator is easily overflowing
    return new DataSetImpl2(mx, new ArrayVec(targets));
  }


  public static void writeModel(Trans result, File to, ModelsSerializationRepository serializationRepository) throws IOException {
    BFGrid grid = grid(result);
    StreamTools.writeChars(CharSequenceTools.concat(result.getClass().getCanonicalName(), "\t", Boolean.toString(grid != null), "\n",
                           serializationRepository.write(result)), to);
  }

  public static Trans readModel(String fileName, ModelsSerializationRepository serializationRepository) throws IOException, ClassNotFoundException {
    final LineNumberReader modelReader = new LineNumberReader(new InputStreamReader(new FileInputStream(fileName)));
    String line = modelReader.readLine();
    CharSequence[] parts = CharSequenceTools.split(line, '\t');
    Class<? extends Trans> modelClazz = (Class<? extends Trans>)Class.forName(parts[0].toString());
    return serializationRepository.read(StreamTools.readReader(modelReader), modelClazz);
  }

  public static BFGrid grid(Trans result) {
    if (result instanceof CompositeTrans) {
      final CompositeTrans composite = (CompositeTrans) result;
      BFGrid grid = grid(composite.f);
      grid = grid == null ? grid(composite.g) : grid;
      return grid;
    }
    else if (result instanceof FuncJoin) {
      final FuncJoin join = (FuncJoin) result;
      for (Func dir : join.dirs()) {
        final BFGrid grid = grid(dir);
        if (grid != null)
          return grid;
      }
    }
    else if (result instanceof TransJoin) {
      final TransJoin join = (TransJoin) result;
      for (Trans dir : join.dirs) {
        final BFGrid grid = grid(dir);
        if (grid != null)
          return grid;
      }
    }
    else if (result instanceof Ensemble) {
      final Ensemble ensemble = (Ensemble) result;
      for (Trans dir : ensemble.models) {
        final BFGrid grid = grid(dir);
        if (grid != null)
          return grid;
      }
    }
    else if (result instanceof ObliviousTree)
      return ((ObliviousTree)result).grid();
    if (result instanceof ObliviousMultiClassTree)
      return ((ObliviousMultiClassTree)result).binaryClassifier().grid();
    return null;
  }

  public static DataSetImpl getSubset(DataSetImpl source, int[] idxs) {
    Mx newMx = new VecBasedMx(source.xdim(),
        new IndexTransVec(
            source.data(),
            new RowsPermutation(idxs, source.xdim()))
    );
    Vec newTarget = new IndexTransVec(
        source.target(),
        new ArrayPermutation(idxs)
    );
    return new DataSetImpl(newMx, newTarget);
  }

  public static DataSet extendDataset(DataSet sourceDS, Mx addedColumns) {
    Vec[] columns = new Vec[addedColumns.columns()];
    for (int i = 0; i < addedColumns.columns(); i++) {
      columns[i] = addedColumns.col(i);
    }
    return extendDataset(sourceDS, columns);
  }

  public static DataSet extendDataset(DataSet sourceDS, Vec... addedColumns) {
    if (addedColumns.length == 0)
      return sourceDS;

    Mx oldData = sourceDS.data();
    Mx newData = new VecBasedMx(oldData.rows(), oldData.columns() + addedColumns.length);
    for (MxIterator iter = oldData.nonZeroes(); iter.advance(); ) {
      newData.set(iter.row(), iter.column(), iter.value());
    }
    for (int i = 0; i < addedColumns.length; i++) {
      for (VecIterator iter = addedColumns[i].nonZeroes(); iter.advance(); ) {
        newData.set(iter.index(), oldData.columns() + i, iter.value());
      }
    }
    return new DataSetImpl(newData, sourceDS.target());
  }

  public static Pair<DataSet, DataSet> splitCv(DataSet learn, double percentage, Random rnd) {
    final TIntList learnIndices = new TIntLinkedList();
    final TIntList testIndices = new TIntLinkedList();
    for (int i = 0; i < learn.power(); i++) {
      if (rnd.nextDouble() < percentage)
        learnIndices.add(i);
      else
        testIndices.add(i);
    }
    final int[] learnIndicesArr = learnIndices.toArray();
    final int[] testIndicesArr = testIndices.toArray();
    return Pair.<DataSet, DataSet>create(
        new DataSetImpl(
            new VecBasedMx(
                learn.xdim(),
                new IndexTransVec(
                    learn.data(),
                    new RowsPermutation(learnIndicesArr, learn.xdim())
                )
            ),
            new IndexTransVec(learn.target(), new ArrayPermutation(learnIndicesArr))
        ),
        new DataSetImpl(
            new VecBasedMx(
                learn.xdim(),
                new IndexTransVec(
                    learn.data(),
                    new RowsPermutation(testIndicesArr, learn.xdim())
                )
            ),
            new IndexTransVec(learn.target(), new ArrayPermutation(testIndicesArr))));
  }

  public static Vec value(Mx ds, Func f) {
    Vec result = new ArrayVec(ds.rows());
    for (int i = 0; i < ds.rows(); i++) {
      result.set(i, f.value(ds.row(i)));
    }
    return result;
  }

  public static <LocalLoss extends StatBasedLoss> WeightedLoss<LocalLoss> bootstrap(LocalLoss loss, FastRandom rnd) {
    int[] poissonWeights = new int[loss.xdim()];
    for (int i = 0; i < loss.xdim(); i++) {
      poissonWeights[i] = rnd.nextPoisson(1.);
    }
    return new WeightedLoss<LocalLoss>(loss, poissonWeights);
  }

  public static Computable<Vec, ? extends Func> targetByName(final String name) {
    try {
      @SuppressWarnings("unchecked")
      Class<Func> oracleClass = (Class<Func>)Class.forName("com.spbsu.ml.loss." + name);
      final Constructor<Func> constructor = oracleClass.getConstructor(Vec.class);
      return new Computable<Vec, Func>() {
        @Override
        public Func compute(Vec argument) {
          try {
            return constructor.newInstance(argument);
          } catch (Exception e) {
            throw new RuntimeException("Exception during metric " + name + " initialization", e);
          }
        }
      };
    }
    catch (Exception e) {
      throw new RuntimeException("Unable to create requested target: " + name, e);
    }
  }

  public enum NormalizationType {
    SPHERE,
    PCA,
    SCALE
  }

  public static class NormalizationProperties {
    public Vec xMean;
    public Mx xTrans;
    public double yMean;
    public double yVar;
  }

  public static DataSet normalize(DataSet ds, NormalizationType type, NormalizationProperties props) {
    final Vec mean = new ArrayVec(ds.xdim());
    final Mx covar = new VecBasedMx(ds.xdim(), ds.xdim());
    double targetMean;
    double targetVar;
    Mx trans;
    {
      DSIterator it = ds.iterator();
      double tSum = 0.;
      double tSum2 = 0.;
      while (it.advance()) {
        VecTools.append(mean, it.x());
        tSum += it.y();
        tSum2 += it.y() * it.y();
      }
      targetMean = tSum / ds.power();
      targetVar = Math.sqrt((tSum2 - ds.power() * targetMean * targetMean) / ds.power());
      VecTools.scale(mean, -1./ds.power());
    }
    Vec temp = new ArrayVec(ds.xdim());
    for (int i = 0; i < ds.power(); i++) {
      Vec vec = ds.data().row(i);
      VecTools.assign(temp, vec);
      VecTools.append(temp, mean);
      VecTools.addOuter(covar, temp, temp);
    }
    VecTools.scale(covar, 1./ds.power());
    switch (type) {
      case SPHERE:
        final Mx l = MxTools.choleskyDecomposition(covar);
        trans = MxTools.inverseLTriangle(l);
        break;
      case PCA:
        trans = new VecBasedMx(ds.xdim(), ds.xdim());
        MxTools.eigenDecomposition(covar, new VecBasedMx(ds.xdim(), ds.xdim()), trans);
        break;
      case SCALE:
        trans = new VecBasedMx(ds.xdim(), ds.xdim());
        for (int i = 0; i < trans.columns(); i++) {
          trans.set(i, i, 1./Math.sqrt(covar.get(i, i)));
        }
        break;
      default:
        throw new NotImplementedException();
    }
    Vec newTarget = VecTools.copy(ds.target());
    Mx newData = VecTools.copy(ds.data());
    for (int i = 0; i < ds.power(); i++) {
      Vec row = newData.row(i);
      VecTools.append(row, mean);
      VecTools.assign(row, MxTools.multiply(trans, row));

      newTarget.set(i, (newTarget.get(i) - targetMean) / targetVar);
    }
    props.xMean = mean;
    props.xTrans = trans;
    props.yMean = targetMean;
    props.yVar = targetVar;
    return new DataSetImpl(newData, newTarget);
  }

}