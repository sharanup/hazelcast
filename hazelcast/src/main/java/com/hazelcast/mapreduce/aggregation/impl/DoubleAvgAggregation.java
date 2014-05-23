/*
 * Copyright (c) 2008-2013, Hazelcast, Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hazelcast.mapreduce.aggregation.impl;

import com.hazelcast.mapreduce.Collator;
import com.hazelcast.mapreduce.Combiner;
import com.hazelcast.mapreduce.CombinerFactory;
import com.hazelcast.mapreduce.Mapper;
import com.hazelcast.mapreduce.Reducer;
import com.hazelcast.mapreduce.ReducerFactory;
import com.hazelcast.mapreduce.aggregation.Aggregation;
import com.hazelcast.mapreduce.aggregation.Supplier;

import java.util.Map;

public class DoubleAvgAggregation<Key, Value>
        implements Aggregation<Key, Value, Key, Double, AvgTuple<Long, Double>, AvgTuple<Long, Double>, Double> {

    @Override
    public Collator<Map.Entry<Key, AvgTuple<Long, Double>>, Double> getCollator() {
        return new Collator<Map.Entry<Key, AvgTuple<Long, Double>>, Double>() {
            @Override
            public Double collate(Iterable<Map.Entry<Key, AvgTuple<Long, Double>>> values) {
                long count = 0;
                double amount = 0;
                for (Map.Entry<Key, AvgTuple<Long, Double>> entry : values) {
                    AvgTuple<Long, Double> tuple = entry.getValue();
                    count += tuple.getFirst();
                    amount += tuple.getSecond();
                }
                return amount / count;
            }
        };
    }

    @Override
    public Mapper<Key, Value, Key, Double> getMapper(Supplier<Key, Value, Double> supplier) {
        return new SupplierConsumingMapper<Key, Value, Double>(supplier);
    }

    @Override
    public CombinerFactory<Key, Double, AvgTuple<Long, Double>> getCombinerFactory() {
        return new DoubleAvgCombinerFactory<Key>();
    }

    @Override
    public ReducerFactory<Key, AvgTuple<Long, Double>, AvgTuple<Long, Double>> getReducerFactory() {
        return new DoubleAvgReducerFactory<Key>();
    }

    static final class DoubleAvgCombinerFactory<Key>
            implements CombinerFactory<Key, Double, AvgTuple<Long, Double>> {

        @Override
        public Combiner<Key, Double, AvgTuple<Long, Double>> newCombiner(Key key) {
            return new DoubleAvgCombiner<Key>();
        }
    }

    static final class DoubleAvgReducerFactory<Key>
            implements ReducerFactory<Key, AvgTuple<Long, Double>, AvgTuple<Long, Double>> {

        @Override
        public Reducer<Key, AvgTuple<Long, Double>, AvgTuple<Long, Double>> newReducer(Key key) {
            return new DoubleAvgReducer<Key>();
        }
    }

    private static final class DoubleAvgCombiner<Key>
            extends Combiner<Key, Double, AvgTuple<Long, Double>> {

        private long count;
        private double amount;

        @Override
        public void combine(Key key, Double value) {
            count++;
            amount += value;
        }

        @Override
        public AvgTuple<Long, Double> finalizeChunk() {
            long count = this.count;
            double amount = this.amount;
            this.count = 0;
            this.amount = 0;
            return new AvgTuple<Long, Double>(count, amount);
        }
    }

    private static final class DoubleAvgReducer<Key>
            extends Reducer<Key, AvgTuple<Long, Double>, AvgTuple<Long, Double>> {

        private volatile long count;
        private volatile double amount;

        @Override
        public void reduce(AvgTuple<Long, Double> value) {
            count += value.getFirst();
            amount += value.getSecond();
        }

        @Override
        public AvgTuple<Long, Double> finalizeReduce() {
            return new AvgTuple<Long, Double>(count, amount);
        }
    }
}
