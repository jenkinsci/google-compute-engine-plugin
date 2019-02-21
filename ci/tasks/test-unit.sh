#!/bin/bash

set -e -u -x

pushd plugin
  mvn test -e
popd
