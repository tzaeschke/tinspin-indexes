
[![Build Status](https://github.com/tzaeschke/tinspin-indexes/actions/workflows/build.yml/badge.svg)](https://github.com/tzaeschke/tinspin-indexes/actions/)
[![codecov](https://codecov.io/gh/tzaeschke/tinspin-indexes/branch/master/graph/badge.svg)](https://codecov.io/gh/tzaeschke/tinspin-indexes)

TinSpin Indexes
===============
This is a library of in-memory indexes. They are used in the TinSpin [TinSpin project](http://www.tinspin.org). The library includes:

 - Several versions of **critbit** index, with support for 64bit keys (fastest), very long keys, or multi-dimensional keys (interleaved with z-ordering). See details below.
 - A **CoverTree** implementation which is loosely based on the "Faster Cover Trees" by M. Izbicki and C.R. Shelton
 - A **kD-Tree** implementation. The kD-Tree provides separate implementations for 1NN-queries and kNN-queries. It also has an optimization that allows it to use a faster code-path as long as no elements with partially equal coordinates have been removed (see javadoc in code).  
 - An adapter for the [**PH-Tree**](http://www.phtree.org). This is only an example integration. For high performance applications it is strongly recommended to use the PH-Tree API directly to be able to use features such as reusable iterators, reusable result objects, other data converters, or custom distance functions. 
 - Several multi-dimensional **quadtree** indexes with separate implementations for point data and rectangle data. The implementations are 'region-quadtrees', they split space in 2^k quadratic quadrants in each level.
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
	<version>1.8.0</version>
</dependency>
```
  
## Changelog

**NOTE: The next release 2.0.0 will have a major API rewrite**.

See [CHANGELOG](CHANGELOG.md) for details.
 - 1.8.0 Full multimap support; many fixes; rewrote all kNN searches; Java 11.  
 - 1.7.1 Dependency on latest PH-Tree
 - 1.7.0 CoverTree and improved index statistics
 - 1.6.1 Improved kD-Tree performance
 - 1.5.1 Fixed for integration of quadtree HC v2
 - 1.5.0 Added quadtree HC v2
 - 1.4.0 Added kD-Tree

## Performance
Some hints to improve performance:
- For kD-trees, try disabling defensive copy via `IndexConfig`. "Defensive copying" creates a copy of all `double[]` 
  when inserted into the tree. Avoiding this copy may slightly improve performance and garbage collection but risks 
  tree inconsistencies when modifying the key externally. Other indexes may also become inconsistent, 
  but it is more severe for kD-tree because they use keys as positions for nodes.  


## CritBit

A Critical Bit tree for k-dimensional or arbitrary length keys.
(Also called: binary patricia trie, radix-tree, ...)

Current version: 

v1.4: Added KD-Tree and adapter for PH-Tree

v1.3: Reduced memory consumption

v1.2: Refactoring, API improvements, slight performance improvements

v1.1: Slight performance improvements
  
v1.0: Initial release

This is a Java implementation of a crit-bit tree. 
A crit-bit tree is a Patricie-Trie for binary data. Patricia-Tries achieve space efficiency by using prefix sharing. 
They are also update efficient because they are 'stable' trees, meaning that any update will affect at most two nodes.

Unlike other crit-bit trees, this tree also supports multi-dimensional data by interleaving the bits of each 
dimension and allowing k-dimensional queries on the tree.

Alternatively it supports 1-dimensional keys with arbitrary length (>64 bit).


