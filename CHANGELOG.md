# Changelog
All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](http://keepachangelog.com/en/1.1.0/)
and this project adheres to [Semantic Versioning](http://semver.org/spec/v2.0.0.html).

## [unreleased]

### Changed
- Moved to GitHub Actions CI. [#13](https://github.com/tzaeschke/tinspin-indexes/pull/13)
- Java JDK 11 default + updated maven dependencies + updated CHANGELOG.md. 
  [#12](https://github.com/tzaeschke/tinspin-indexes/pull/12)

### Added
- Proper test (and fixes) for multimaps.[#15](https://github.com/tzaeschke/tinspin-indexes/pull/15)

## [1.7.1] - 2018-12-10

### Changed

- Depend on latest PH-Tree unbounded version 2.0++

## [1.7.0] - 2018-12-03

### Changed

- Depend on latest PH-Tree v2.0.1 [2018-07-21]
- Updated R-Tree to use new kNN algorithm, a variant of Hjaltason and Samet [2018-05-19]
- API Change: Query iterator's reset() now returns 'this'. [2018-05-19]
- Update 20 use PH-Tree 2.0.1 [2018-05-19]

## Added

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

[Unreleased]: https://github.com/tzaeschke/tinspin-indexes/compare/tinspin-indexes-1.7.1...HEAD
[1.7.1]: https://github.com/tzaeschke/tinspin-indexes/compare/tinspin-indexes-1.7.0...tinspin-indexes-1.7.1
[1.7.0]: https://github.com/tzaeschke/tinspin-indexes/compare/tinspin-indexes-1.6.1...tinspin-indexes-1.7.0
[1.6.1]: https://github.com/tzaeschke/tinspin-indexes/compare/tinspin-indexes-1.6.0...tinspin-indexes-1.6.1
[1.6.0]: https://github.com/tzaeschke/tinspin-indexes/compare/tinspin-indexes-1.5.1...tinspin-indexes-1.6.0
[1.5.1]: https://github.com/tzaeschke/tinspin-indexes/compare/tinspin-indexes-1.5.0...tinspin-indexes-1.5.1
[1.5.0]: https://github.com/tzaeschke/tinspin-indexes/compare/tinspin-indexes-1.4.0...tinspin-indexes-1.5.0
[1.4.0]: https://github.com/tzaeschke/tinspin-indexes/compare/tinspin-indexes-1.3.6...tinspin-indexes-1.4.0