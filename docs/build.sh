#!/bin/sh -e

if ! readlink swift.css > /dev/null
then
  echo "Create a soft link to stc/.../docs/swift.css!"
  exit 1
fi

asciidoc --attribute stylesheet=${PWD}/swift.css internals.txt
asciidoc --attribute stylesheet=${PWD}/swift.css sites.txt
cp internals.html turbine-internals.html
cp sites.html     turbine-sites.html
