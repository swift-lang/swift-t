#!/bin/sh

if [[ ! -z ${R_LIBS_USER+x} ]]; then
  export R_LIBS_USER_BACKUP="$R_LIBS_USER"
fi
export R_LIBS_USER="${CONDA_PREFIX}/lib/R/library"