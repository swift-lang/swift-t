#!/usr/bin/env bash

# Helper script to automatically find source root by searching
# upwards from the specified directory.

set -e
THIS=$( dirname $0 )
STARTDIR=$1
MAX_HEIGHT=$2

if [ -z "${MAX_HEIGHT}" ]
then
  MAX_HEIGHT=3
fi

if [ ! -d "${STARTDIR}" ]
then
  echo "Expected valid directory: $STARTDIR"
  exit 1
fi

EXP_SUBDIRS="stc turbine lb c-utils"

curr_dir=${STARTDIR}
for i in $(seq $MAX_HEIGHT)
do
  all_present=1
  for subdir in ${EXP_SUBDIRS}
  do
    if [ ! -d ${curr_dir}/${subdir} ]
    then
      all_present=0
      break
    fi
  done

  if (( all_present ))
  then
    src_root=$(cd ${curr_dir}; pwd)
    echo "Located Swift/T source root at ${src_root}" 1>&2
    echo "$src_root"
    exit 0
  fi

  curr_dir=${curr_dir}/..
done


echo "Could not locate swift-t source root by searching up ${MAX_HEIGHT} levels from ${STARTDIR}"
exit 1
