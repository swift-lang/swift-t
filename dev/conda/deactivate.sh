#!/bin/sh

if [[ ! -z ${R_LIBS_USER_BACKUP+x} ]]; then
  export R_LIBS_USER="$R_LIBS_USER_BACKUP"
  unset R_LIBS_USER_BACKUP
else
  unset R_LIBS_USER
fi