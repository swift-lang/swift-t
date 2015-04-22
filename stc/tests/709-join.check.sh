#!/bin/sh -e

if ! grep s:a:bc:d:e:f:g ${TURBINE_OUTPUT}
then
  echo "Correct output string not found!"
  exit 1
fi

if ! grep fs:1.0,2.5,3.25 ${TURBINE_OUTPUT}
then
  echo "Correct output string not found!"
  exit 1
fi
exit 0

