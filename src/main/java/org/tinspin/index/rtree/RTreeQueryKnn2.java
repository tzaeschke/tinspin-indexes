/*
 * Copyright 2009-2023 Tilmann Zaeschke. All rights reserved.
 *
 * This file is part of TinSpin.
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
package org.tinspin.index.rtree;

import org.tinspin.index.QueryIteratorKNN;
import org.tinspin.index.RectangleDistanceFunction;
import org.tinspin.index.RectangleEntry;
import org.tinspin.index.RectangleEntryDist;
import org.tinspin.index.util.MinMaxHeapZ;
import org.tinspin.index.util.MinMaxHeapZ2;

import java.util.NoSuchElementException;
import java.util.function.Predicate;

public class RTreeQueryKnn2<T> implements QueryIteratorKNN<RectangleEntryDist<T>> {

    private final RTree<T> tree;
    private final RectangleDistanceFunction distFn;
    private final Predicate<RectangleEntry<T>> filterFn;
    MinMaxHeapZ2<NodeDistT> queueN = MinMaxHeapZ2.create((t1, t2) -> t1.dist < t2.dist);
    MinMaxHeapZ2<DistEntry<T>> queueV = MinMaxHeapZ2.create((t1, t2) -> t1.dist() < t2.dist());
    double maxNodeDist = Double.POSITIVE_INFINITY;
    private DistEntry<T> current;
    private int remaining;
    private double[] center;
    private double currentDistance;

    RTreeQueryKnn2(RTree<T> tree, int minResults, double[] center, RectangleDistanceFunction distFn, Predicate<RectangleEntry<T>> filterFn) {
        this.filterFn = filterFn;
        this.distFn = distFn;
        this.tree = tree;
        reset(center, minResults);
    }

    @Override
    public QueryIteratorKNN<RectangleEntryDist<T>> reset(double[] center, int minResults) {
        this.center = center;
        this.currentDistance = Double.MAX_VALUE;
        this.remaining = minResults;
        this.maxNodeDist = Double.POSITIVE_INFINITY;
        this.current = null;
        if (minResults <= 0 || tree.getRoot() == null) {
            return this;
        }
        queueN.clear();
        queueV.clear();

        queueN.push(new NodeDistT(0, tree.getRoot()));
        FindNextElement();
        return this;
    }

    @Override
    public boolean hasNext() {
        return current != null;
    }

    @Override
    public DistEntry<T> next() {
        if (!hasNext()) {
            throw new NoSuchElementException();
        }
        DistEntry<T> ret = current;
        FindNextElement();
        return ret;
    }

    public double distance() {
        return currentDistance;
    }

    private void FindNextElement() {
        while (remaining > 0 && !(queueN.isEmpty() && queueV.isEmpty())) {
            boolean useV = !queueV.isEmpty();
            if (useV && !queueN.isEmpty()) {
                useV = queueV.peekMin().dist() <= queueN.peekMin().dist;
            }
            if (useV) {
                // data entry
                DistEntry<T> result = queueV.peekMin(); // TODO
                queueV.popMin();
                --remaining;
                this.current = result;
                currentDistance = result.dist();
                return;
            } else {
                // inner node
                NodeDistT top = queueN.peekMin();
                queueN.popMin();
                RTreeNode<T> node = top.node;
                double dNode = top.dist;

                if (dNode > maxNodeDist && queueV.size() >= remaining) {
                    // ignore this node
                    continue;
                }

                if (node instanceof RTreeNodeLeaf) {
                    for (Entry<T> entry : node.getEntries()) {
                        if (filterFn.test(entry)) {
                            double d = distFn.dist(center, entry);
                            // Using '<=' allows dealing with infinite distances.
                            if (d <= maxNodeDist) {
                                queueV.push(new DistEntry<>(entry.min, entry.max, entry.value(), d));
                                if (queueV.size() >= remaining) {
                                    if (queueV.size() > remaining) {
                                        queueV.popMax();
                                    }
                                    double dMax = queueV.peekMax().dist();
                                    maxNodeDist = Math.min(maxNodeDist, dMax);
                                }
                            }
                        }
                    }
                } else {
                    for (Entry<T> o : node.getEntries()) {
                        RTreeNode<T> subnode = (RTreeNode<T>) o;
                        double dist = distFn.dist(center, subnode);
                        if (dist <= maxNodeDist) {
                            queueN.push(new NodeDistT(dist, subnode));
                        }
                    }
                }
            }
        }
        current = null;
        currentDistance = Double.MAX_VALUE;
    }

    private class NodeDistT {
        double dist;
        RTreeNode<T> node;

        public NodeDistT(double dist, RTreeNode<T> node) {
            this.dist = dist;
            this.node = node;
        }
    }
}

