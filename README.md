[![Build Status](https://travis-ci.org/tzaeschke/tinspin-indexes.svg?branch=master)](https://travis-ci.org/tzaeschke/tinspin-indexes)
[![codecov](https://codecov.io/gh/tzaeschke/tinspin-indexes/branch/master/graph/badge.svg)](https://codecov.io/gh/tzaeschke/tinspin-indexes)

TinSpin Indexes
===============
This is a library of in-memory indexes. They are used in the TinSpin [TinSpin project](http://www.tinspin.org). The library includes:

 - Several versions of **critbit** index, with support for 64bit keys (fastest), very long keys, or multi-dimensional keys (interleaved with z-ordering). See details below.
 - A **kD-Tree** implementation. The kD-Tree provides separate implementations for 1NN-queries and kNN-queries. It also has a an optimization  that allows it to use a faster code-path as long as no elements with partially equal coordinates have been removed (see javadoc in code).  
 - An adapter for the **PH-Tree**. This is only an example integration. For high performance applications it is strongly recommended to use the PH-Tree API directly to be able to use features such as reusable iterators, reusable result objects, other data converters, or custom distance functions. 
 - Several multi-dimensional **quadtree** indexes with separate implementations for point data and rectangle data. The implementations are 'region-quadtrees', they split space in 2^k quadratic quadrants in each level.
     - **qtplain** is a standard quadtree implementation
     - **qthypercube** is a quadtree that has a fixed node size of 2^k slots per node, even if not all slots are filled with subnodes or entries. This causes much worse scaling of memory requirements (with dimensionality k), however, it allows much better scaling (also with k) of query and update times. 
 - A multi-dimensional **R*Tree** index.
 - A multi-dimensional **STR-Tree** index (same as R*Tree, but with sort-tile-recursive bulk loading). 
 
TinSpin indexes are also available via maven:

```
<dependency>
	<groupId>org.tinspin</groupId>
	<artifactId>tinspin-indexes</artifactId>
	<version>1.4.0</version>
</dependency>
```
  

CritBit
=======

A Critical Bit tree for k-dimensional or arbitrary length keys.
(Also called: binary patricia trie, radix-tree, ...)

Current version: 

v1.4: Added KD-Tree and adapter for PH-Tree

v1.3: Reduced memory consumption

v1.2: Refactoring, API improvements, slight performance improvements

v1.1: Slight performance improvements
  
v1.0: Initial release

This is a Java implementation of a crit-bit tree. 
A crit-bit tree is a Patricie-Trie for binary data. Patricia-Tries have are very space efficient due to their prefix sharing. They are also update efficent because they are 'stable' trees, meaning that any update will affect at most two nodes.

Unlike other crit-bit trees, this tree also supports multi-dimensional data by interleaving the bits of each dimension and allowing k-dimensional queries on the tree.

Alternatively it supports 1-dimensional keys with arbitrary length (>64 bit).


