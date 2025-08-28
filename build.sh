#!/bin/zsh

# BUILD.SH
# Build all STC docs

# Usage: ./build.sh <OPTIONS> <TARGETS>
# Options: -B is forwarded to make
# Targets: Passed to make

# Installation:
# On Ubuntu, install APT package asciidoc,
# then make sure PYTHONPATH contains /usr/lib/python3/dist-packages

B=""
zparseopts -D -E B=B

export TURBINE_HOME=${HOME}/proj/swift-t/turbine/code
# Force system python3 for Asciidoc
PATH=/usr/bin:$PATH

make ${B} -f build.mk ${*}
