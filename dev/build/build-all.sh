#!/usr/bin/env bash
# Build script with default settings

set -e
THISDIR=`dirname $0`

source "${THISDIR}/init-settings.sh"
source "${THISDIR}/internal-build-all.sh"
