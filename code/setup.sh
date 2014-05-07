#!/bin/sh

crash()
{
  MSG=$1
  echo "ADLB/setup.sh: ${MSG}"
  exit 1
}

echo "Running autoheader..."
autoheader || crash "autoconf failed!"
echo "Running autoconf..."
autoreconf -f || crash "autoconf failed!"
echo "ADLB/setup.sh: OK"
exit 0
