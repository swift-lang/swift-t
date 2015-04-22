#!/usr/bin/env bash
set -e

warn_count=$(grep "WARN" ${STC_ERR_FILE} | wc -l)

echo "Warnings: $warn_count"

if (( warn_count != 1 ))
then
  echo "Expected only 1 warning in ${STC_ERR_FILE} but got ${warn_count}"
  cat ${STC_ERR_FILE}
  exit 1
fi

exit 0
