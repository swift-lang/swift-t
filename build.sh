#!/bin/zsh

# BUILD.SH
# Build all STC docs

# Usage: ./build.sh <OPTIONS> <TARGETS>
# Options: -B is forwarded to make
# Targets: Passed to make

B=""
zparseopts -D -E B=B

export TURBINE_HOME=${HOME}/swift-t/turbine/code

make ${B} -f build.mk ${*}
