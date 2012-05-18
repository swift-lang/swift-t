#!/bin/sh

crash()
{
  MSG=$1
  echo "setup.sh: ${MSG}"
  exit 1
}

autoconf   || crash "autoconf failed!"
autoheader || crash "autoheader failed!"

exit 0
