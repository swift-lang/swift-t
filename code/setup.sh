#!/bin/sh

crash()
{
  MSG=$1
  echo "ADLB/setup.sh: ${MSG}"
  exit 1
}

autoconf || crash "autoconf failed!"
echo "ADLB/setup.sh: OK"
exit 0
