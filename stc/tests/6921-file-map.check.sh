#!/bin/bash
set -eu

F=./6921-test.txt
if [ ! -f $F ]; then
  echo "Expected file $F"
  exit 1
fi

if ! grep -q "import files" $F; then
  echo "$F does not have expected contents"
  exit 1
fi

rm 6921-test.txt
