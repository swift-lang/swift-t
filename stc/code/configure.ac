
# STC CONFIGURE.AC

# Process this file with autoconf to produce a configure script.

# This is only used to build the Debian package

define([stc_version],
       regexp(esyscmd(cat etc/version.txt),[\([.0-9]*\)],[\1]))


AC_PREREQ([2.62])
AC_INIT([STC], [stc_version()], [wozniak@mcs.anl.gov])
AC_CONFIG_SRCDIR([build.xml])

# Debian stuff: noops
AC_ARG_ENABLE([dependency-tracking])
AC_ARG_ENABLE([maintainer-mode])
AC_ARG_ENABLE([silent-rules])

AC_CONFIG_FILES(Makefile)

AC_OUTPUT
