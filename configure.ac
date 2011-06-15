
# Process this file with autoconf to produce a configure script.

# NOTE: Default CFLAGS are "-g -O2"
# Use ./configure CFLAGS="-g ..." to changs the defaults

AC_PREREQ([2.67])
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

AC_ARG_WITH(adlb,
                 AS_HELP_STRING(
                 [--with-woztools],
                 [location of ADLB]),
                 [AC_CHECK_FILE(${withval}/libadlb.so,
                  [], [USE_ADLB=0])
                  if test "$USE_ADLB" = 0 ; then
                     AC_MSG_ERROR(Could not find ADLB in $withval)
                  fi
                  AC_MSG_RESULT([using woztools in $withval ...])
                  AC_DEFINE_UNQUOTED([ADLB_LOCATION],
                                     "$withval", [ADLB Location])
                  USE_ADLB=$withval
                 ],
                 [
                  AC_MSG_ERROR([Not found: ADLB])
                  USE_ADLB=0
                 ])
AC_SUBST(USE_ADLB)

AC_ARG_WITH(mpi,
                 AS_HELP_STRING(
                 [--with-mpi],
                 [location of MPI]),
                 [AC_CHECK_FILE(${withval}/lib/libmpich.so,
                  [], [USE_MPI=0])
                  if test "$USE_MPI" = 0 ; then
                     AC_MSG_ERROR(Could not find MPI in $withval)
                  fi
                  AC_MSG_RESULT([using MPI in $withval ...])
                  AC_DEFINE_UNQUOTED([MPI_LOCATION],
                                     "$withval", [MPI Location])
                  USE_MPI=$withval
                 ],
                 [
                  AC_MSG_ERROR([Not found: MPI])
                  USE_MPI=0
                 ])
AC_SUBST(USE_MPI)


AC_CONFIG_FILES(
        src/turbine/module.mk
        src/tools/module.mk
        src/progs/module.mk
        test/module.mk
	Makefile
)

AC_OUTPUT

