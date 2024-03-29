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
package org.tinspin.index.qtplain;

import org.tinspin.index.PointDistance;
import org.tinspin.index.util.MinHeap;
import org.tinspin.index.util.MinMaxHeap;

import java.util.NoSuchElementException;
import java.util.function.Predicate;

import static org.tinspin.index.Index.*;
import static org.tinspin.index.qtplain.QUtil.distToRectNode;

public class QIteratorKnn<T> implements PointIteratorKnn<T> {

    private final QNode<T> root;
    private final PointDistance distFn;
    private final PointFilterKnn<T> filterFn;
    MinHeap<NodeDistT> queueN = MinHeap.create((t1, t2) -> t1.dist < t2.dist);
    MinMaxHeap<PointEntryKnn<T>> queueV = MinMaxHeap.create((t1, t2) -> t1.dist() < t2.dist());
    double maxNodeDist = Double.POSITIVE_INFINITY;
    private PointEntryKnn<T> current;
    private int remaining;
    private double[] center;
    private double currentDistance;

    QIteratorKnn(QNode<T> root, int minResults, double[] center, PointDistance distFn, PointFilterKnn<T> filterFn) {
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

        queueN.push(new NodeDistT(0, root));
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
                useV = queueV.peekMin().dist() <= queueN.peekMin().dist;
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
                NodeDistT top = queueN.peekMin();
                queueN.popMin();
                QNode<T> node = top.node;
                double dNode = top.dist;

                if (dNode > maxNodeDist && queueV.size() >= remaining) {
                    // ignore this node
                    continue;
                }

                if (node.isLeaf()) {
                    for (PointEntry<T> entry : node.getEntries()) {
                        double d = distFn.dist(center, entry.point());
                        // Using '<=' allows dealing with infinite distances.
                        if (filterFn.test(entry, d) && d <= maxNodeDist) {
                            queueV.push(new PointEntryKnn<>(entry, d));
                            if (queueV.size() >= remaining) {
                                if (queueV.size() > remaining) {
                                    queueV.popMax();
                                }
                                double dMax = queueV.peekMax().dist();
                                maxNodeDist = Math.min(maxNodeDist, dMax);
                            }
                        }
                    }
                } else {
                    for (QNode<T> subnode : node.getChildNodes()) {
                        double dist = distToRectNode(center, subnode.getCenter(), subnode.getRadius(), distFn);
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
        QNode<T> node;

        public NodeDistT(double dist, QNode<T> node) {
            this.dist = dist;
            this.node = node;
        }
    }
}

