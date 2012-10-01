#!/bin/sh
cat "$1"
cp "$1" "$2"
echo "$3" >> "$2"
echo "$4" >> "$2"
