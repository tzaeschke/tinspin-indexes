[![Build Status](https://travis-ci.org/tzaeschke/critbit.svg?branch=master)](https://travis-ci.org/tzaeschke/critbit)
[![codecov](https://codecov.io/gh/tzaeschke/critbit/branch/master/graph/badge.svg)](https://codecov.io/gh/tzaeschke/critbit)

ZooDB Indexes
=============
This is a library of in-memory indexes. The library includes:

 - Several versions of **critbit** index, with support for 64bit keys (fastest), very long keys, or multi-dimensional keys (interleaved with z-ordering). See details below.
 - Several multi-dimensional **quadtree** indexes with separate implementations for point data and rectangle data. The implementations are 'region-quadtrees', they split space in 2^k quadratic quadrants in each level.
 - A multi-dimensional **R*Tree** index.
 
  

critbit
=======

A Critical Bit tree for k-dimensional or arbitrary length keys.
(Also called: binary patricia trie, radix-tree, ...)

Current version: 

v1.3: Reduced memory consumption

v1.2: Refactoring, API improvements, slight performance improvements

v1.1: Slight performance improvements
  
v1.0: Initial release

This is a Java implementation of a crit-bit tree. 
A crit-bit tree is a Patricie-Trie for binary data. Patricia-Tries have are very space efficient due to their prefix sharing. They are also update efficent because they are 'stable' trees, meaning that any update will affect at most two nodes.

Unlike other crit-bit trees, this tree also supports multi-dimensional data by interleaving the bits of each dimension and allowing k-dimensional queries on the tree.

Alternatively it supports 1-dimensional keys with arbitrary length (>64 bit).


