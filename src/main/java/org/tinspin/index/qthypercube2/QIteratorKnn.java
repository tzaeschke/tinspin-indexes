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
package org.tinspin.index.qthypercube2;

import org.tinspin.index.PointDistanceFunction;
import org.tinspin.index.PointEntry;
import org.tinspin.index.PointEntryDist;

import java.util.NoSuchElementException;
import java.util.PriorityQueue;
import java.util.function.Predicate;

public class QIteratorKnn<T> {

    private class NodeDistT {
        double dist;
        QNode<T> node;

        public NodeDistT(double dist, QNode<T> node) {
            this.dist = dist;
            this.node = node;
        }
    }

    //    min_max_vector_heap<NodeDistT, CompareNodeDistByDistance> queue_n_;
    //    min_max_vector_heap<ValueDistT, CompareValueDistByDistance> queue_v_;
    //    ::phtree::bptree::detail::priority_queue<EntryDistT, CompareEntryDistByDistance> queue_n_;
    //    ::phtree::bptree::detail::priority_queue<EntryDistT, CompareEntryDistByDistance> queue_v_;
    // TODO try minmaxheap
    PriorityQueue<NodeDistT> queue_n_ = new PriorityQueue<>(); // TODO compare
    PriorityQueue<QEntryDist<T>> queue_v_ = new PriorityQueue<>(); // TODO compare

    double max_node_dist_ = Double.POSITIVE_INFINITY;
    private PointEntryDist<T> current;
    private QNode<T> root;
    private int min_results;
    private int remaining;
    private double[] center;
    private double currentDistance;
    private PointDistanceFunction distFn;
    private Predicate<PointEntry<T>> filterFn;

//        class CompareNodeDistByDistance {
//        boolean test(NodeDistT left, NodeDistT right) {
//        return left.first > right.first;
//        }
//        }
//
//        class CompareValueDistByDistance {
//        boolean test(ValueDistT left, ValueDistT right) {
//        return left.first > right.first;
//        }
//        }


    QIteratorKnn(QNode<T> root, int min_results, double[] center, PointDistanceFunction distFn, Predicate<PointEntry<T>> filterFn) {

        this.center = center;
        this.currentDistance = Double.MAX_VALUE;
        remaining = min_results;
        this.filterFn = filterFn;
        this.distFn = distFn;

        if (min_results <= 0 || root == null) {
            this.current = null;
            return;
        }

        queue_n_.add(new NodeDistT(0, root));
        FindNextElement();
    }

    public boolean hasNext() {
        return current != null;
    }

    public PointEntryDist<T> next() {
        if (!hasNext()) {
            throw new NoSuchElementException();
        }
        PointEntryDist<T> ret = current;
        FindNextElement();
        return ret;
    }

    double distance() {
        return currentDistance;
    }

    private void FindNextElement() {
        while (remaining > 0 && !(queue_n_.isEmpty() && queue_v_.isEmpty())) {
            boolean use_v = !queue_v_.isEmpty();
            if (use_v && !queue_n_.isEmpty()) {
                use_v = queue_v_.peek().dist() <= queue_n_.peek().dist;
            }
            if (use_v) {
                // data entry
                PointEntryDist<T> result = queue_v_.poll();
                --remaining;
                this.current = result;
                currentDistance = result.dist();
                return;
            } else {
                // inner node
                NodeDistT top = queue_n_.poll();
                QNode<T> node = top.node;
                double d_node = top.dist;

                if (d_node > max_node_dist_ && queue_v_.size() >= remaining) {
                    // ignore this node
                    continue;
                }

//                if (node.isLeaf()) {
//                    for (QEntry<T> entry : node.getValues()) {
//                        if (filterFn.test(entry)) {
//                            double d = distFn.dist(center, entry.point());
//                            // Using '<=' allows dealing with infinite distances.
//                            if (d <= max_node_dist_) {
//                                queue_v_.add(new QEntryDist<>(entry, d));
//                                if (queue_v_.size() >= remaining) {
//                                    if (queue_v_.size() > remaining) {
//                                        queue_v_.pop_max();
//                                    }
//                                    double d_max = queue_v_.top_max().first;
//                                    max_node_dist_ = Math.min(max_node_dist_, d_max);
//                                }
//                            }
//                        }
//                    }
//                } else {
//                    for (QNode<T> subnode : node.getEntries()) {
//                        double dist = distToRectNode(center, subnode.getCenter(), subnode.getRadius(), distance_);
//                        if (dist <= max_node_dist_) {
//                            queue_n_.add(new NodeDistT(dist, subnode));
//                        }
//                    }
//                }
            }
        }
        current = null;
        currentDistance = Double.MAX_VALUE;
    }
}

