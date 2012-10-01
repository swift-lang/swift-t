#!/bin/sh
# app that returns an error return code
OUT=$1
echo "hello" > "$OUT"
return 1
