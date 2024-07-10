#!/usr/bin/env zsh
set -eu

# STC FILE LIST

# Produces list of files for make-release-pkg and mk-upstream-tgz
# Also makes a soft link to the Debian package files

# Does not contain the tests- the Debian package does not get them

print build.xml configure{,.ac} Makefile.in
print maint/{{debian,version}.mkf,file-list.zsh}
print etc/help/*.txt etc/{version.txt,turbine-version.txt}
print src/exm/stc/ast/ExM.g
print bin/* etc/{stc-config.sh,log4j2.xml}
print **/*.java
print lib/*.jar
print META-INF/MANIFEST.MF
print README

if [[ ${PKG_TYPE} == "deb-bin" ]]
then
  ln -sfT maint/debian debian
  print debian/*[^~]
fi

return 0
