#!/bin/sh -eu

if [ ! -f swift.css ]
then
  echo "Make a soft link to swift.css!"
  exit 1
fi

asciidoc --attribute stylesheet=$PWD/swift.css \
                    -a max-width=750px -a textwidth=80 downloads.txt
