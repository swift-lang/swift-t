#!/bin/sh -eu

asciidoc --attribute stylesheet=$PWD/swift.css \
                    -a max-width=750px -a textwidth=80 downloads.txt
