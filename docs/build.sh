#!/bin/sh

asciidoc --attribute stylesheet=${PWD}/swift.css -a max-width=750px -a textwidth=80 swift.txt
asciidoc --attribute stylesheet=${PWD}/swift.css -a max-width=750px -a textwidth=80 internals.txt
