#!/bin/sh

# Remove Debian work directories

find . \( -name ".deb-work-*" -o -name "*-deb-tgz-*" \) -a -type d | \
  xargs -r -n 1 -- rm -r
