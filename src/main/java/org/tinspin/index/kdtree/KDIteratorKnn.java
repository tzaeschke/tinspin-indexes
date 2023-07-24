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
package org.tinspin.index.kdtree;

import org.tinspin.index.*;
import org.tinspin.index.util.MinHeap;
import org.tinspin.index.util.MinMaxHeap;

import java.util.NoSuchElementException;

import static org.tinspin.index.Index.*;

public class KDIteratorKnn<T> implements PointIteratorKnn<T> {

    private final Node<T> root;
    private final PointDistance distFn;
    private final PointFilterKnn<T> filterFn;
    MinHeap<NodeDist<T>> queueN = MinHeap.create((t1, t2) -> t1.closestDist < t2.closestDist);
    MinMaxHeap<PointEntryKnn<T>> queueV = MinMaxHeap.create((t1, t2) -> t1.dist() < t2.dist());
    double maxNodeDist = Double.POSITIVE_INFINITY;
    private PointEntryKnn<T> current;
    private int remaining;
    private double[] center;
    private double currentDistance;

    KDIteratorKnn(Node<T> root, int minResults, double[] center, PointDistance distFn, PointFilterKnn<T> filterFn) {
        this.filterFn = filterFn;
        this.distFn = distFn;
        this.root = root;
        reset(center, minResults);
    }

    @Override
    public PointIteratorKnn<T> reset(double[] center, int minResults) {
        this.center = center;
        this.currentDistance = Double.MAX_VALUE;
        this.remaining = minResults;
        this.maxNodeDist = Double.POSITIVE_INFINITY;
        this.current = null;
        if (minResults <= 0 || root == null) {
            return this;
        }
        queueN.clear();
        queueV.clear();

        // Initialize queue, use d=0 because every imaginable point lies inside the root Node
        double[] closest = center.clone();
        queueN.push(new NodeDist<>(distFn.dist(center, closest), root, closest));
        findNextElement();
        return this;
    }

    @Override
    public boolean hasNext() {
        return current != null;
    }

    @Override
    public PointEntryKnn<T> next() {
        if (!hasNext()) {
            throw new NoSuchElementException();
        }
        PointEntryKnn<T> ret = current;
        findNextElement();
        return ret;
    }

    public double distance() {
        return currentDistance;
    }

    private void findNextElement() {
        while (remaining > 0 && !(queueN.isEmpty() && queueV.isEmpty())) {
            boolean useV = !queueV.isEmpty();
            if (useV && !queueN.isEmpty()) {
                useV = queueV.peekMin().dist() <= queueN.peekMin().closestDist;
            }
            if (useV) {
                // data entry
                PointEntryKnn<T> result = queueV.peekMin();
                queueV.popMin();
                --remaining;
                this.current = result;
                currentDistance = result.dist();
                return;
            } else {
                // inner node
                NodeDist<T> entry = queueN.peekMin();
                queueN.popMin();

                if (entry.closestDist > maxNodeDist && queueV.size() >= remaining) {
                    // ignore this node
                    continue;
                }

                Node<T> node = entry.node;
                double d = distFn.dist(center, node.point());
                // Using '<=' allows dealing with infinite distances.
                if (filterFn.test(node, d) && d <= maxNodeDist) {
                    queueV.push(new PointEntryKnn<>(node, d));
                    if (queueV.size() >= remaining) {
                        if (queueV.size() > remaining) {
                            queueV.popMax();
                        }
                        double dMax = queueV.peekMax().dist();
                        maxNodeDist = Math.min(maxNodeDist, dMax);
                    }
                }
                // left
                if (node.getLo() != null) {
                    createEntryLo(entry);
                }
                // right
                if (node.getHi() != null) {
                    createEntryHi(entry);
                }
            }
        }
        current = null;
        currentDistance = Double.POSITIVE_INFINITY;
    }

    void createEntryHi(NodeDist<T> entry) {
        Node<T> node = entry.node;
        int splitDim = node.getDim();
        double[] newClosest;
        double newClosestDist;
        double splitX = node.point()[splitDim];
        if (center[splitDim] < splitX) {
            newClosest = entry.closest.clone();  // copy
            newClosest[splitDim] = splitX;
            newClosestDist = distFn.dist(newClosest, center);
        } else {
            newClosest = entry.closest;
            newClosestDist = entry.closestDist;
        }
        if (newClosestDist <= maxNodeDist) {
            queueN.push(new NodeDist<>(newClosestDist, node.getHi(), newClosest));
        }
    }

    void createEntryLo(NodeDist<T> entry) {
        Node<T> node = entry.node;
        int splitDim = node.getDim();
        double[] newClosest;
        double newClosestDist;
        double splitX = node.point()[splitDim];
        if (center[splitDim] > splitX) {
            newClosest = entry.closest.clone();  // copy
            newClosest[splitDim] = splitX;
            newClosestDist = distFn.dist(newClosest, center);
        } else {
            newClosest = entry.closest;
            newClosestDist = entry.closestDist;
        }

        if (newClosestDist <= maxNodeDist) {
            queueN.push(new NodeDist<>(newClosestDist, node.getLo(), newClosest));
        }
    }

    private static class NodeDist<T> {
        double closestDist;
        Node<T> node;
        // The point in the nodes region that is closest to the query point
        double[] closest;
        NodeDist(double closestDist, Node<T> node, double[] closest) {
            this.closestDist = closestDist;
            this.node = node;
            this.closest = closest;
        }
    }
}

