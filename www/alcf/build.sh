#!/bin/bash
set -eu

# Before running, create a soft link to
# https://svn.mcs.anl.gov/repos/exm/www/css/swift.css

cd $(dirname $0)

asciidoc --attribute stylesheet=${PWD}/swift.css \
                    -a max-width=750px -a textwidth=80 alcf.txt
