package com.spbsu.ml.methods.seq.automaton.evaluation;

import com.spbsu.commons.func.Computable;
import com.spbsu.commons.math.MathTools;
import com.spbsu.ml.methods.seq.automaton.AutomatonStats;

public class OptimizedStateEvaluation<T> implements Computable<AutomatonStats<T>, Double> {
  @Override
  public Double compute(AutomatonStats<T> automatonStats) {
    double score = 0;
    final int stateCount = automatonStats.getAutomaton().getStateCount();
    for (int i = 0; i < stateCount; i++) {
      final double sum = automatonStats.getStateSum().get(i);
      final double sum2 = automatonStats.getStateSum2().get(i);
      final double size = automatonStats.getStateWeight().get(i);
      if (size > MathTools.EPSILON) {
        score += sum2 - sum * sum / size;
      }
    }

    return score;
  }
}