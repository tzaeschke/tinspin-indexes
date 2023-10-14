
[![Build Status](https://github.com/tzaeschke/tinspin-indexes/actions/workflows/build.yml/badge.svg)](https://github.com/tzaeschke/tinspin-indexes/actions/)
[![codecov](https://codecov.io/gh/tzaeschke/tinspin-indexes/branch/master/graph/badge.svg)](https://codecov.io/gh/tzaeschke/tinspin-indexes)

TinSpin Indexes
===============
This is a library of in-memory indexes. They are used in the TinSpin [TinSpin project](http://www.tinspin.org). The library includes:

 - Several versions of **critbit** index, with support for 64bit keys (fastest), very long keys, or multi-dimensional keys (interleaved with z-ordering). See details below.
 - A **CoverTree** implementation which is loosely based on the "Faster Cover Trees" by M. Izbicki and C.R. Shelton
 - A **kD-Tree** implementation. The kD-Tree provides separate implementations for 1NN-queries and kNN-queries. It also has an optimization that allows it to use a faster code-path as long as no elements with partially equal coordinates have been removed (see javadoc in code).  
 - An adapter for the [**PH-Tree**](http://www.phtree.org). This is only an example integration. For high performance applications it is strongly recommended to use the PH-Tree API directly to be able to use features such as reusable iterators, reusable result objects, other data converters, or custom distance functions. 
 - Several multi-dimensional **quadtree** indexes with separate implementations for point data and box data. The implementations are 'region-quadtrees', they split space in 2^k quadratic quadrants in each level.
     - **qtplain** is a standard quadtree implementation
     - **qthypercube** is a quadtree that has a fixed node size of 2^k slots per node, even if not all slots are filled with subnodes or entries. This causes much worse scaling of memory requirements (with dimensionality k), however, it allows much better scaling (also with k) of query and update times. 
     - **qthypercube2** a more space efficient version of qthypercube that allows directory nodes to also contain data entries.
 - A multi-dimensional **R*Tree** index.
 - A multi-dimensional **STR-Tree** index (same as R*Tree, but with sort-tile-recursive bulk loading). 
 
TinSpin indexes are also available via maven:

```
<dependency>
    <groupId>org.tinspin</groupId>
    <artifactId>tinspin-indexes</artifactId>
    <version>2.1.1</version>
</dependency>
```
  
## Overview
The indexes fall into four categories, depending on whether they use points or (axis-aligned) boxes as keys and whether they are maps or [multimaps](https://en.wikipedia.org/wiki/Multimap). This results in four base types with respective Java interfaces:
- `PointMap` is supported by `CoverTree`, `KDTree`, `PHTreeP`, `PointArray`, `QuadtreeKD0`, `QuadtreeKD`, `QuadtreeKD2` and `RTree` (via `PointMapWrapper`)
- `PointMultimap` is supported by `KDTree`, `PHTreeMMP`, `PointArray`, `QuadtreeKD0`, `QuadtreeKD`, `QuadtreeKD2` and `RTree` (via `PointMultimapWrapper`)
- `BoxMap` is supported by `PHTreeR`, `BoxArray`, `QuadtreeKD0`, `QuadtreeKD` and `RTree`
- `BoxMultimap` is supported by `BoxArray`, `QuadtreeKD0`, `QuadtreeKD` and `RTree`

Indexes can be created via factories in the interfaces, e.g. `PointMap.Factory.createKdTree(...)`.

**WARNING** *The `Map` implementations are mostly not strict with respect to unique keys. That means they work fine if keys are unique. However, they may not enforce uniqueness (replace entries when the same key is added twice) and instead always add another entry. That means they may effectively act as multimaps.* At the moment, only PH-Tree based indexes enforce uniqueness and properly overwrite existing keys.

Note:
 - **STR-Trees** are simply R-Trees that are preloaded using the STR algorithm. THis can be done with
   the factory methods `....Factory.createAndLoadStrRTree(...)`.
 - `PointArray` and `BoxArray` are simple array based implementations. They scale badly with size, their only use is for verifying correctness of other indexes. 

## Changelog

See [CHANGELOG](CHANGELOG.md) for details.
 - 2.1.1 Added `create()` method for `IndexConfig`.
 - 2.1.0 **API:** Added factory methods for all indexes.
 - 2.0.1 Fixed issue with dependencies in generated pom.
 - **2.0.0** **Major API rewrite.**
 - 1.8.0 Full multimap support; many fixes; rewrote all kNN searches; Java 11.  
 - 1.7.1 Dependency on latest PH-Tree
 - 1.7.0 CoverTree and improved index statistics
 - 1.6.1 Improved kD-Tree performance
 - 1.5.1 Fixed for integration of quadtree HC v2
 - 1.5.0 Added quadtree HC v2
 - 1.4.0 Added kD-Tree

## Performance
Some hints to improve performance:
- Use the `reset()` method of iterators to reuse them instead of creating (complex) iterator objects for each query. This should reduce garbage collection load.  
- For kD-trees, try disabling defensive copy via `IndexConfig`. "Defensive copying" creates a copy of all `double[]` 
  when inserted into the tree. Avoiding this copy may slightly improve performance and garbage collection but makes the tree more 
  vulnerable to inconsistencies when modifying the key externally. Other indexes may also become inconsistent, 
  but it is more severe for kD-tree because they use keys as positions for nodes.  


## CritBit

A Critical Bit tree for k-dimensional or arbitrary length keys.
(Also called: binary patricia trie, radix-tree, ...)

Current version: 

 - v1.4: Added KD-Tree and adapter for PH-Tree
 - v1.3: Reduced memory consumption
 - v1.2: Refactoring, API improvements, slight performance improvements
 - v1.1: Slight performance improvements
 - v1.0: Initial release

This is a Java implementation of a crit-bit tree. 
A [crit-bit tree](https://cr.yp.to/critbit.html) is a 
[Patricia-Trie](https://en.wikipedia.org/wiki/Radix_tree#History)
for binary data. Patricia-Tries achieve space efficiency by using prefix sharing. 
They are also update efficient because they are 'stable' trees, meaning that any update will affect at most two nodes.

Unlike other crit-bit trees, this tree also supports multi-dimensional data by interleaving the bits of each 
dimension and allowing k-dimensional queries on the tree.

Alternatively it supports 1-dimensional keys with arbitrary length (>64 bit).


