#!/bin/bash
set -eu

# SHOW VERSIONS
# from all Swift/T components

THIS=$( cd $( dirname $0 ) ; /bin/pwd )

echo "THIS:            $THIS"

source $THIS/get-versions.sh

# These  are automatically extracted:
echo "CUTILS_VERSION:  $CUTILS_VERSION"
echo "ADLBX_VERSION:   $ADLBX_VERSION"
echo "TURBINE_VERSION: $TURBINE_VERSION"
echo "STC_VERSION:     $STC_VERSION"
