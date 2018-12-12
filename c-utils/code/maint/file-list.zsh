#!/usr/bin/env zsh
set -eu

# FILE LIST
# Produces list of files for make-release-pkg and mk-upstream-tgz
# Also makes a soft link to the Debian package files,
# either for the bin or dev package

print c-utils-config.h.in configure configure.ac README.txt
print bootstrap Makefile.in src/module.mk.in
print maint/*sh maint/*.mkf
print src/*.[ch] src/c-utils.h.in
print tests/module.mk.in
print tests/*.[ch]
print version.txt NOTICE

if [[ ${PKG_TYPE} == "deb-dev" ||
      ${PKG_TYPE} == "deb-bin" ]]
then
  DEB_TYPE=${PKG_TYPE#deb-} # Chop off "deb-"
  ln -sfT maint/debian-${DEB_TYPE} debian
  print debian/*[^~]
fi
