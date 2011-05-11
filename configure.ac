#                                               -*- Autoconf -*-
# Process this file with autoconf to produce a configure script.

AC_PREREQ([2.68])
AC_INIT([FULL-PACKAGE-NAME], [VERSION], [BUG-REPORT-ADDRESS])
AC_CONFIG_SRCDIR([test/data.c])
AC_CONFIG_HEADERS([config.h])

# Checks for programs.
AC_PROG_CC
# This does not work:
# AC_PROG_INSTALL

# Checks for libraries.

# Checks for header files.

# Checks for typedefs, structures, and compiler characteristics.
AC_HEADER_STDBOOL

# Checks for library functions.
AC_FUNC_MALLOC
AC_CHECK_FUNCS([strdup])

AC_ARG_WITH(woztools,
                 AS_HELP_STRING(
                 [--with-woztools],
                 [location of WOZTOOLS]),
                 [AC_CHECK_FILE(${withval}/lib/libwoztools.a,
                  [], [USE_WOZTOOLS=0])
                  if test "$USE_WOZTOOLS" = 0 ; then
                     AC_MSG_ERROR(Could not find WOZTOOLS in $withval)
                  fi
                  AC_MSG_RESULT([using woztools in $withval ...])
                  AC_DEFINE_UNQUOTED([WOZTOOLS_LOCATION],
                                     "$withval", [WOZTOOLS Location])
                  USE_WOZTOOLS=$withval
                 ],
                 [
                  AC_MSG_ERROR([Not found: WOZTOOLS])
                  USE_WOZTOOLS=0
                 ])
AC_SUBST(USE_WOZTOOLS)

AC_CONFIG_FILES(
        src/module.mk
        test/module.mk
	Makefile
)

AC_OUTPUT

