# Changelog
All notable changes to this project will be documented in this file.

## [2.0.0] - 2019-02-26
### Added
- One-shot instances feature
- Create snapshot option for one-shot instances

### Changes
- Changed Jenkins version to 2.107.1

### Fixed
- Changed script to build Windows image to accommodate updates in OpenSSH for Windows

## [1.0.9] - 2019-01-30
### Added
- Cleanup of nodes that weren't terminated properly

### Fixed
- Null issue for no vm template selected

## [1.0.8] - 2019-01-14
### Added
- Compute Engine Instance templates feature added

### Changes
- Instance cap based on number of executors, not on number of instances
- Increased the timeout for integration tests

### Fixed
- Labels from instance configuration now properly allocated to instances
- Fixed typo in documentation - license file
- Fixed typo in jelly help file
