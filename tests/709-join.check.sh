#!/bin/sh -e

if ! grep s:a:bc:d:e:f:g ${TURBINE_OUTPUT}
then
  echo "Correct output string not found!"
  exit 1
fi
exit 0

