#!/usr/bin/env bash
set -e

# Swift/T build script with default settings

THISDIR=$( dirname $0 )

source "${THISDIR}/init-settings.sh"
source "${THISDIR}/internal-build-all.sh"
