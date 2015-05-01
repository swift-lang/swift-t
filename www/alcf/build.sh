#!/bin/bash
set -eu

cd $(dirname $0)

asciidoc --attribute stylesheet=${PWD}/swift.css \
                    -a max-width=750px -a textwidth=80 alcf.txt
