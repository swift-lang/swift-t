#!/bin/sh

crash()
{
  MSG=$1
  echo "Turbine: setup.sh: ${MSG}"
  exit 1
}

echo "Running autoconf..."
autoconf -f || crash "autoconf failed!"
echo "Running autoheader..."
autoheader || crash "autoheader failed!"
echo "Turbine: setup.sh: OK"

exit 0
