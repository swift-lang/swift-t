#!/bin/bash

# MK-DOC
# This builds the README for local viewing, however,
# most readers will just look at the automatically
# rendered version on GitHub.

# Swift/T gh-pages branch checkout:
SWIFT_GHP=$HOME/swift-t.ghp

asciidoc --attribute stylesheet=$SWIFT_GHP/swift.css \
         --attribute max-width=800px \
         README.adoc
