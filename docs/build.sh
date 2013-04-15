#!/bin/sh

asciidoc --attribute stylesheet=${PWD}/swift.css swift.txt
asciidoc --attribute stylesheet=${PWD}/swift.css internals.txt
