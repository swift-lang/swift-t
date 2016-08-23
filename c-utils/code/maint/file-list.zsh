#!/bin/zsh
set -eu

# Produce list of files for make-release-pkg and mk-upstream-tgz

print c-utils-config.h.in configure configure.ac README.txt
print bootstrap Makefile.in src/module.mk.in
print maint/*sh maint/*.mk
print src/*.[ch]
print c-utils-config.h.in src/c-utils.h.in
print tests/module.mk.in
print tests/*.[ch]
print version.txt NOTICE

print FILE LIST $PWD ${DEBIAN_PKG_TYPE} >&2

if (( ${+DEBIAN_PKG_TYPE} ))
then
  ln -sfT debian-${DEBIAN_PKG_TYPE} debian
  print debian/*[^~]
fi
print FILE LIST $PWD ${DEBIAN_PKG_TYPE} DONE >&2
