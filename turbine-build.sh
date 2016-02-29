#!/bin/sh
set -e

asciidoc --attribute stylesheet=${PWD}/swift.css internals.txt
asciidoc --attribute stylesheet=${PWD}/swift.css sites.txt
cp internals.html turbine-internals.html
cp sites.html     turbine-sites.html
