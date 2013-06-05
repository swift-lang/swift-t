#!/bin/sh

OUTPUT=020-string-multiline.out
grep -q 1234  ${OUTPUT} || exit 1
grep -q 4321  ${OUTPUT} || exit 1
grep -q "x=1" ${OUTPUT} || exit 1

echo OK

exit 0
