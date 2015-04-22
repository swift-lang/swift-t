#!/bin/bash
set -eu

cd $( dirname $0 )

for D in *
do
  if [[ -d ${D} ]] && [[ -x ${D}/clean.sh ]]
  then
    cd ${D}
    echo "clean: ${D}"
    ./clean.sh
    cd ..
  fi
done

exit 0
