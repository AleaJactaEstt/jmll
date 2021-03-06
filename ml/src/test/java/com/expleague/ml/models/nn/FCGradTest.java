package com.expleague.ml.models.nn;

import com.expleague.commons.math.vectors.Vec;
import com.expleague.commons.math.vectors.VecTools;
import com.expleague.commons.math.vectors.impl.vectors.ArrayVec;
import com.expleague.commons.random.FastRandom;
import com.expleague.ml.func.generic.Sigmoid;
import com.expleague.ml.func.generic.Sum;
import com.expleague.ml.models.nn.layers.ConstSizeInput;
import com.expleague.ml.models.nn.layers.FCLayerBuilder;
import com.expleague.ml.models.nn.layers.OneOutLayer;
import org.junit.Test;

import static junit.framework.TestCase.assertEquals;

public class FCGradTest {
  private static final NeuralSpider<Vec> spider = new NeuralSpider<>();
  private static final int ROUNDS = 30;
  private static final FastRandom rng = new FastRandom();
  private static final double EPS = 1e-6;

  @Test
  public void testOneLayer() {
    for (int nIn = 1; nIn <= 100; nIn += 5) {
      for (int nOut = 1; nOut <= 100; nOut += 5) {
        System.out.println("Test [" + nIn + ", " + nOut + "]");
        final NetworkBuilder<Vec>.Network network = new NetworkBuilder<>(
            new ConstSizeInput(nIn))
            .append(new FCLayerBuilder().nOut(nOut).activation(Sigmoid.class))
            .build(new OneOutLayer());

        testNN(network, nIn, nOut);
      }
    }
  }

  @Test
  public void testMultiLayer() {
    for (int i = 0; i < 100; i++) {
      final int numLayers = rng.nextInt(20) + 2;
      final int[] dims = new int[numLayers];
      generateDims(dims);

      System.out.print("Test [");
      for (int j = 0; j < dims.length - 1; j++) {
        System.out.print(dims[j] + ", ");
      }
      System.out.println(dims[dims.length - 1] + "]");

      NetworkBuilder<Vec> builder = new NetworkBuilder<>(new ConstSizeInput(dims[0]));
      for (int j = 1; j < numLayers; j++) {
        FCLayerBuilder fcLayerBuilder = new FCLayerBuilder().nOut(dims[j]);
        if (j != numLayers - 1) {
          fcLayerBuilder.activation(Sigmoid.class);
        }
        builder.append(fcLayerBuilder);
      }
      final NetworkBuilder<Vec>.Network network = builder.build(new OneOutLayer());

      testNN(network, dims);
    }
  }

  @Test
  public void testSmallMultiLayer() {
    for (int i = 0; i < 100; i++) {
      final int numLayers = rng.nextInt(20) + 2;
      final int[] dims = new int[numLayers];
      generateSmallDims(dims);

      System.out.print("Test [");
      for (int j = 0; j < dims.length - 1; j++) {
        System.out.print(dims[j] + ", ");
      }
      System.out.println(dims[dims.length - 1] + "]");

      NetworkBuilder<Vec> builder = new NetworkBuilder<>(new ConstSizeInput(dims[0]));
      for (int j = 1; j < numLayers; j++) {
        FCLayerBuilder fcLayerBuilder = new FCLayerBuilder().nOut(dims[j]);
        builder.append(fcLayerBuilder);
      }
      final NetworkBuilder<Vec>.Network network = builder.build(new OneOutLayer());

      testNN(network, dims);
    }
  }

  private void testNN(NetworkBuilder<Vec>.Network network, int... dims) {
    final Vec weights = new ArrayVec(network.wdim());
    final Vec weightsCopy = new ArrayVec(network.wdim());
    Vec arg = new ArrayVec(dims[0]);
    Vec gradWeight = new ArrayVec(network.wdim());

    for (int i = 0; i < ROUNDS; i++) {
      VecTools.fillUniform(weights, rng);

      VecTools.assign(weightsCopy, weights);

      VecTools.fillUniform(arg, rng);

      final Vec state = spider.compute(network, arg, weights);
      final double stateSum = VecTools.sum(state);

      spider.parametersGradient(network, arg, new Sum(), weights, gradWeight);

      for (int round = 0; round < ROUNDS; round++) {
        final int wIdx = rng.nextInt(network.wdim());
        weightsCopy.adjust(wIdx, EPS);
        final double incState = VecTools.sum(spider.compute(network, arg, weightsCopy));
        double grad = (incState - stateSum) / EPS;

        assertEquals("test idx " + wIdx, grad, gradWeight.get(wIdx), EPS);

        weightsCopy.adjust(wIdx, -EPS);
      }
    }
  }

  private static void generateDims(int[] dims) {
    for (int i = 0; i < dims.length; i++) {
      dims[i] = rng.nextInt(100) + 5;
    }
  }

  private static void generateSmallDims(int[] dims) {
    for (int i = 0; i < dims.length; i++) {
      dims[i] = 1;
    }
  }
}
