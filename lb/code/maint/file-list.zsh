#!/usr/bin/env zsh
set -eu

# ADLB/X FILE LIST
# Produces list of files for make-release-pkg and mk-src-tgz
# Also makes a soft link to the Debian package files,
# either for the Debian bin or dev package

print bootstrap configure configure.ac Makefile.in install-sh
print config.h.in
print maint/*.sh maint/*.c
print maint/{debian,version}.mkf
print m4/*.m4
print src/*.[ch]
print src/{adlb-version.h.in,mpe-settings.h.in}
print {src,tests}/module.mk.in apps/module.mk
print version.txt NOTICE

if [[ ${PKG_TYPE} == deb-* ]]
then
  DEB_TYPE=${PKG_TYPE#deb-} # Chop off "deb-"
  ln -sfT maint/debian-${DEB_TYPE} debian
  print debian/*[^~]
fi
