#!/bin/zsh -f
set -eu

# STC FILE LIST

# Produces list of files for make-release-pkg and mk-upstream-tgz
# Also makes a soft link to the Debian package files

# Does not contain the tests- the Debian package does not get them

print build.xml configure{,.ac} Makefile{,.in}
print maint/{debian.mk,debian-list.mk,version.mk,file-list.zsh}
print etc/help*.txt etc/{version.txt,turbine-version.txt}
print src/exm/stc/ast/ExM.g
print bin/* etc/stc-config.sh
print **/*.java
print lib/*.jar
print META-INF/MANIFEST.MF
print README

if (( ${+DEBIAN_PKG_TYPE} ))
then
  ln -sfT maint/debian debian
  print debian/*[^~]
fi

return 0
