#!/usr/bin/env bash

warns=$(grep "WARN" ${STC_ERR_FILE})

if [ "$warns" != "" ]
then
  echo "Expected no warnings in ${STC_ERR_FILE} but got some:"
  cat ${STC_ERR_FILE}
  exit 1
fi

for i in $(seq 0 31); do
    f="f-${i}.txt"
    if [ ! -f "$f" ]; then
        echo "Expected $f to exist"
        exit 1;
    fi
    contents=$(cat $f)

    if [ "$contents" != "hello world" ]
    then
      echo "Contents don't match: \"$contents\""
      exit 1
    fi

    rm $f
done
