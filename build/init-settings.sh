#!/usr/bin/env bash

if [ ! -f "${THISDIR}/exm-settings.sh" ]; then
  cp ${THISDIR}/exm-settings.sh.template ${THISDIR}/exm-settings.sh
fi

