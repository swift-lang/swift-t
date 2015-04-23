#!/usr/bin/env bash

THISDIR=$( cd $(dirname $0) && pwd )

if [ ! -f "${THISDIR}/exm-settings.sh" ]; then
  cp "${THISDIR}/exm-settings.sh.template" "${THISDIR}/exm-settings.sh"
  echo "Created ${THISDIR}/exm-settings.sh"
else
  echo "Already exists: ${THISDIR}/exm-settings.sh"
fi

source "${THISDIR}/exm-settings.sh"
echo "Sourced ${THISDIR}/exm-settings.sh"
echo "Installation target is: EXM_PREFIX=${EXM_PREFIX}"
echo "You may edit exm-settings.sh and source again before building."
