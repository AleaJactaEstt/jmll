package com.spbsu.ml.methods.seq;

import com.spbsu.commons.math.vectors.Vec;
import com.spbsu.commons.math.vectors.VecTools;
import com.spbsu.commons.math.vectors.impl.vectors.ArrayVec;
import com.spbsu.commons.seq.CharSeq;
import com.spbsu.commons.seq.Seq;
import com.spbsu.commons.seq.regexp.DynamicCharAlphabet;
import com.spbsu.ml.data.set.DataSet;
import com.spbsu.ml.loss.LLLogit;
import com.spbsu.ml.methods.hmm.BaumWelch;
import org.junit.Test;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class BWTest {

  @Test
  public void testBW1() {
    final DynamicCharAlphabet alpha = new DynamicCharAlphabet();
    final List<CharSeq> lines = Stream.of("10101010101010", "010101010101")
        .map(CharSeq::create)
        .peek(line -> {
          for (int i = 0; i < line.length(); i++) {
            alpha.index(line, i);
          }
        }).collect(Collectors.toList());
    Collections.shuffle(lines);
    final List<CharSeq> seqs = lines.subList(0, Math.min(1000, lines.size()));
    final DataSet.Stub<Seq<Character>> dataSet = new DataSet.Stub<Seq<Character>>(null) {
      @Override
      public CharSeq at(int i) {
        return seqs.get(i);
      }
      @Override
      public int length() {
        return seqs.size();
      }
      @Override
      public Class<CharSeq> elementType() {
        return CharSeq.class;
      }
    };
    final BaumWelch<Character> bw = new BaumWelch<>(alpha, 2, 10);
    final Vec target = new ArrayVec(dataSet.length());
    VecTools.fill(target, 1);
    bw.fit(dataSet, new LLLogit(target, dataSet));

  }
}
