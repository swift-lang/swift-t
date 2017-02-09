#!/bin/zsh -f
set -eu

# TURBINE FILE LIST
# Produces list of files for make-release-pkg and mk-upstream-tgz
# Also makes a soft link to the Debian package files

print bootstrap config.h.in configure configure.ac
print version.txt README.txt
print Makefile.in **/*.mk.in
print maint/{debian.mkf,version.mkf}
print maint/{*.sh,find-tcl.zsh}
print bin/turbine{,.in}
print bin/turbine-{read,write}-doubles
print scripts/*-config.sh.in scripts/helpers.zsh
print **/*.[cChi] **/*.{tcl,swift} src/**/*.m4
print tests/{runbin.zsh.in,run-mpi.zsh}
print tests/{*.manifest,*.sh,*.data,*.txt}
print src/util/debug-tokens.tcl.in
print src/**/*.manifest
print scripts/{data-log.sh,leak-find.py,rank.zsh}
print scripts/main-wrap/genleaf
print scripts/main-wrap/settings/*.sh
print scripts/mkstatic/*.{sh,template}
print scripts/mkstatic/About.txt
print scripts/submit/*.*sh*
print scripts/submit/cray/*.*sh*
print scripts/submit/cobalt/*turbine*.*sh*
print scripts/submit/ec2/turbine-setup-ec2.zsh
print scripts/submit/pbs/{turbine-pbs-run.zsh,turbine.pbs.m4}
print scripts/submit/slurm/*turbine*.*sh*
print scripts/submit/sge/turbine{-sge-run.zsh,.sge.m4}
print src/turbine/turbine-version.h.in
print etc/help/*.txt

if (( ${+PKG_TYPE} ))
then
  ln -sfT maint/debian debian
  print debian/*[^~]
fi
