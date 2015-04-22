#!/usr/bin/env bash

THISDIR=$( cd $(dirname $0) && pwd )

if [ ! -f "${THISDIR}/exm-settings.sh" ]; then
  cp "${THISDIR}/exm-settings.sh.template" "${THISDIR}/exm-settings.sh"
fi

source "${THISDIR}/exm-settings.sh"
