#!/bin/bash

set -e -u -x

pushd plugin
  mvn verify
popd
