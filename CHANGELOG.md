# Changelog
All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](http://keepachangelog.com/en/1.1.0/)
and this project adheres to [Semantic Versioning](http://semver.org/spec/v2.0.0.html).

## [Unreleased]

- Nothing yet

## [2.1.4 - Unreleased]

- Fixed tree corruption after remove() in QT2. 

## [2.1.3] - 2023-11-19

### Fixed
- Fixed QuadtreeKD2 kNN finding previously deleted entries [#37](https://github.com/tzaeschke/tinspin-indexes/issue/37)

## [2.1.2] - 2023-10-31

### Fixed
- Fixed `create()` method for `IndexConfig` not being static [#33](https://github.com/tzaeschke/tinspin-indexes/issue/35)

## [2.1.1] - 2023-10-14

### Fixed
- Added `create()` method for `IndexConfig` [#33](https://github.com/tzaeschke/tinspin-indexes/issue/33)

## [2.1.0] - 2023-08-12

### Added
- **API change:** Added missing factory methods for STR loaded R-Trees and kD-Tree(IndexConfig)
  [#30](https://github.com/tzaeschke/tinspin-indexes/pull/30)
- **API change:** Added factory classes for indexes, e.g. `PointMap.Factory.createKdTree()`.
  [#29](https://github.com/tzaeschke/tinspin-indexes/pull/29)

### Fixed
- Fix javadoc regressions. [#32](https://github.com/tzaeschke/tinspin-indexes/pull/32)
- Test candidates should counsistently use integers as values.
  [#31](https://github.com/tzaeschke/tinspin-indexes/pull/31)
- Remove erroneous references to GPL 3.0. This library is APL 2.0 only. 
  [#27](https://github.com/tzaeschke/tinspin-indexes/pull/27), [#28](https://github.com/tzaeschke/tinspin-indexes/pull/28)

## [2.0.1] - 2023-08-01

### Fixed
- Removed maven-shade-plugin as it broke the phtree dependency. [#25](https://github.com/tzaeschke/tinspin-indexes/issues/25)
- Added some tests and removed some dead code. [#23](https://github.com/tzaeschke/tinspin-indexes/pull/23)

## [2.0.0] - 2023-07-25

### Changed
- Removed deprecated code, more API fixes and tests [#21](https://github.com/tzaeschke/tinspin-indexes/pull/21)
- **New API:** [#20](https://github.com/tzaeschke/tinspin-indexes/pull/20)
  - Moved a lot of smaller classes & interfaces into `Index` which should be a lot cleaner and simplifies imports to a single `import static org.tinspin.index.Index.*;`.
  - `...Entry` interfaces have been removed and replaced with a common implementation.
  - Renamed `Rectangle` to `Box` to make it shorter
  - Renamed (most) `...Index` to `...Map` and `...IndexMM` to `...Multimap` to make it clearer
  - Simplified return type of `...Index.iterator`.
  - renamed all `KNN` to `Knn` to be more consistent
  - Renamed `...DistanceFunction` to `Distance` which is shorter
  - Renamed `...EntryDist` to `EntryKnn` to be more consistent and clearer
  - New query result interface types: `PointIterator<T>`, `BoxIterator<T>`, ` PointIteratorKnn<T>` and `BoxIteratorKnn<T>` which are more concise.

### Added
- Added codecov Test coverage [#22](https://github.com/tzaeschke/tinspin-indexes/pull/22)


## [1.8.0] - 2023-07-21

### Changed
- Moved to GitHub Actions CI. [#13](https://github.com/tzaeschke/tinspin-indexes/pull/13)
- Java JDK 11 default + updated maven dependencies + updated CHANGELOG.md. 
  [#12](https://github.com/tzaeschke/tinspin-indexes/pull/12)
- removed travis.yml and fixed javadoc. [#18](https://github.com/tzaeschke/tinspin-indexes/pull/18)
- Added javadoc and sources generation to pom.xml. [#19](https://github.com/tzaeschke/tinspin-indexes/pull/19)

### Added
- MinMaxHeap & MinHeap for better kNN queries. [#17](https://github.com/tzaeschke/tinspin-indexes/pull/17)
- Proper API, more tests and numerous fixes for multimaps. [#16](https://github.com/tzaeschke/tinspin-indexes/pull/16)
- Proper test (and fixes) for multimaps. [#15](https://github.com/tzaeschke/tinspin-indexes/pull/15)

## [1.7.1] - 2018-12-10

### Changed

- Depend on latest PH-Tree unbounded version 2.0++

## [1.7.0] - 2018-12-03

### Changed

- Depend on latest PH-Tree v2.0.1 [2018-07-21]
- Updated R-Tree to use new kNN algorithm, a variant of Hjaltason and Samet [2018-05-19]
- API Change: Query iterator's reset() now returns 'this'. [2018-05-19]
- Update 20 use PH-Tree 2.0.1 [2018-05-19]

### Added

- Added CoverTree. [2018-11-28]
- Generic Stats and (optional) kNN distance calculation count [2018-11-28]

## [1.6.1] - 2018-04-03

### Changed

- Improved KD-Tree performance

## [1.6.0] - 2018-04-03

### Changed

- Improved KD-Tree performance

## [1.5.0] - 2017-12-04

- Added QuadtreHC2, i.e. version 2 of the HC quadtree. This version is more space
  efficient by allowing directory nodes to hold data entries, thus avoiding
  leaf nodes that hold just one data entry.

## 1.4.0 - 2017-11-13

- Added KD-Tree.
- Improved QuadtreeStats collection

## 1.3.6 - 2017-09-17

### Changed

- Added adapter for PH-Tree.
- Integrated pull request from chris0385 with improved R-Tree kNN queries
  for large k. [2017-03-04]
- Proper HCI usage for quadtree [2017-01-23]
- Added qtplain [2017-01-23]
- Moved project to org.tinpin [2017-01-23]

## 1.3.5 - 2017-01-07

- Fixed rare problem with postfix creation. This solves a problem
  with kd-queries and slightly reduces memory consumption. See issue #7. [2017-01-07]
- Changed structure to standard maven structure - [2016-09-19]
- Changed License to Apache License 2.0 - [2016-09-19]
- Renamed project from CritBit to zoodb-indexes - [2016-09-19]
- Added quadtree and R*Tree implementations - [2016-09-19]
- Added .travis.yml - [2016-01-04]

## 1.3.4 - 2015-10-20

### Changed

- Merged CB64 and CB64COW - [2015-10-20]
- Implemented resetable iterators - [2015-10-20]

## 1.3.3 - 2015-10-11

### Changed

- Fixed 64COW iterators returning empty Entry for queries that shouldn't return anything. - [2015-10-11]
- Added CHANGELOG - [2015-10-11]
- Pushed to v1.3.3-SNAPSHOT - [2015-10-11]

[Unreleased]: https://github.com/tzaeschke/tinspin-indexes/compare/tinspin-indexes-2.1.3...HEAD
[2.1.3]: https://github.com/tzaeschke/tinspin-indexes/compare/tinspin-indexes-2.1.2...tinspin-indexes-2.1.3
[2.1.2]: https://github.com/tzaeschke/tinspin-indexes/compare/tinspin-indexes-2.1.1...tinspin-indexes-2.1.2
[2.1.1]: https://github.com/tzaeschke/tinspin-indexes/compare/tinspin-indexes-2.1.0...tinspin-indexes-2.1.1
[2.1.0]: https://github.com/tzaeschke/tinspin-indexes/compare/tinspin-indexes-2.0.1...tinspin-indexes-2.1.0
[2.0.1]: https://github.com/tzaeschke/tinspin-indexes/compare/tinspin-indexes-2.0.0...tinspin-indexes-2.0.1
[2.0.0]: https://github.com/tzaeschke/tinspin-indexes/compare/tinspin-indexes-1.8.0...tinspin-indexes-2.0.0
[1.8.0]: https://github.com/tzaeschke/tinspin-indexes/compare/tinspin-indexes-1.7.1...tinspin-indexes-1.8.0
[1.7.1]: https://github.com/tzaeschke/tinspin-indexes/compare/tinspin-indexes-1.7.0...tinspin-indexes-1.7.1
[1.7.0]: https://github.com/tzaeschke/tinspin-indexes/compare/tinspin-indexes-1.6.1...tinspin-indexes-1.7.0
[1.6.1]: https://github.com/tzaeschke/tinspin-indexes/compare/tinspin-indexes-1.6.0...tinspin-indexes-1.6.1
[1.6.0]: https://github.com/tzaeschke/tinspin-indexes/compare/tinspin-indexes-1.5.1...tinspin-indexes-1.6.0
[1.5.1]: https://github.com/tzaeschke/tinspin-indexes/compare/tinspin-indexes-1.5.0...tinspin-indexes-1.5.1
[1.5.0]: https://github.com/tzaeschke/tinspin-indexes/compare/tinspin-indexes-1.4.0...tinspin-indexes-1.5.0
[1.4.0]: https://github.com/tzaeschke/tinspin-indexes/compare/tinspin-indexes-1.3.6...tinspin-indexes-1.4.0
