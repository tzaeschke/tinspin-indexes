critbit
=======

A Critical Bit tree for k-dimensional or arbitrary length keys.
(Also called: binary patricia trie, radix-tree, ...)

Current version: 1.0

This is a Java implementation of a crit-bit tree. 
A crit-bit tree is a Patricie-Trie for binary data. Patricia-Tries have are very space efficient due to their prefix sharing. They are also update efficent because they are 'stable' trees, meaning that any update will affect at most two nodes.

Unlike other crit-bit trees, this tree also supports multi-dimensional data by interleaving the bits of each dimension and allowing k-dimensional queries on the tree.

Alternatively it supports 1-dimensional keys with arbitrary length (>64 bit).


