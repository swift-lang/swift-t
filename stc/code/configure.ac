
# STC CONFIGURE.AC

# Process this file with autoconf to produce a configure script.

# This is only used to build the Debian package

define([stc_version],
       regexp(esyscmd(cat etc/version.txt),[\([.0-9]*\)],[\1]))

AC_PREREQ([2.62])
AC_INIT([STC], [stc_version()], [wozniak@mcs.anl.gov])
AC_CONFIG_SRCDIR([build.xml])

DEBIAN_BUILD=0
AC_ARG_ENABLE(debian-build,
  AS_HELP_STRING(
    [--enable-debian-build], [configure for a Debian package]),
    [DEBIAN_BUILD=1]
)
AC_SUBST(DEBIAN_BUILD)

# Default for Debian is /usr/lib
USE_TURBINE=/usr/lib/turbine
AC_ARG_WITH(turbine,
    AS_HELP_STRING(
        [--with-turbine],
        [location of TURBINE]),
    [ USE_TURBINE=${withval} ]
)

AC_MSG_CHECKING([for $USE_TURBINE/include/turbine.h])
if [[ ! -r $USE_TURBINE/include/turbine.h ]]
then
  AC_MSG_ERROR(Could not find Turbine in $withval)
fi
AC_MSG_RESULT(ok)
AC_SUBST(USE_TURBINE)

USE_JAVA=$( which java )
AC_ARG_WITH(with-java,
    AS_HELP_STRING(
        [--with-java],
        [location of Java VM]),
    [ USE_JAVA=${withval} ]
)
if ! $USE_JAVA -h > /dev/null 2>&1
then
    AC_MSG_ERROR(Java does not work: tried: $USE_JAVA)
fi
AC_MSG_RESULT(Using Java: $USE_JAVA)
AC_SUBST(USE_JAVA)

# Debian stuff: noops
AC_ARG_ENABLE([dependency-tracking])
AC_ARG_ENABLE([maintainer-mode])
AC_ARG_ENABLE([silent-rules])

AC_CONFIG_FILES(Makefile)

AC_OUTPUT
