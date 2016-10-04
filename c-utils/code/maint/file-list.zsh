#!/bin/zsh
set -eu

# FILE LIST
# Produces list of files for make-release-pkg and mk-upstream-tgz
# Also makes a soft link to the Debian package files,
# either for the bin or dev package

print c-utils-config.h.in configure configure.ac README.txt
print bootstrap Makefile.in src/module.mk.in
print maint/*sh maint/*.mk maint/*.mkf
print src/*.[ch]
print c-utils-config.h.in src/c-utils.h.in
print tests/module.mk.in
print tests/*.[ch]
print version.txt NOTICE

if (( ${+DEBIAN_PKG_TYPE} ))
then
  ln -sfT maint/debian-${DEBIAN_PKG_TYPE} debian
  print debian/*[^~]
fi
