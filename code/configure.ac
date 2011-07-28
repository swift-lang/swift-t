
# Process this file with autoconf to produce a configure script.

# NOTE: Default CFLAGS are "-g -O2"
# Use ./configure CFLAGS="-g ..." to changs the defaults

AC_PREREQ([2.67])
AC_INIT([FULL-PACKAGE-NAME], [VERSION], [BUG-REPORT-ADDRESS])
AC_CONFIG_SRCDIR([src/turbine/turbine.c])
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

dnl AC_ARG_WITH(woztools,
dnl                  AS_HELP_STRING(
dnl                  [--with-woztools],
dnl                  [location of WOZTOOLS]),
dnl                  [AC_CHECK_FILE(${withval}/lib/libwoztools.a,
dnl                   [], [USE_WOZTOOLS=0])
dnl                   if test "$USE_WOZTOOLS" = 0 ; then
dnl                      AC_MSG_ERROR(Could not find WOZTOOLS in $withval)
dnl                   fi
dnl                   AC_MSG_RESULT([using woztools in $withval ...])
dnl                   AC_DEFINE_UNQUOTED([WOZTOOLS_LOCATION],
dnl                                      "$withval", [WOZTOOLS Location])
dnl                   USE_WOZTOOLS=$withval
dnl                  ],
dnl                  [
dnl                   AC_MSG_ERROR([Not found: WOZTOOLS])
dnl                   USE_WOZTOOLS=0
dnl                  ])
dnl AC_SUBST(USE_WOZTOOLS)

AC_ARG_WITH(adlb,
                 AS_HELP_STRING(
                 [--with-adlb],
                 [location of ADLB]),
                 [AC_CHECK_FILE(${withval}/libadlb.so,
                  [], [USE_ADLB=0])
                  if test "$USE_ADLB" = 0 ; then
                     AC_MSG_ERROR(Could not find ADLB in $withval)
                  fi
                  AC_MSG_RESULT([using ADLB in $withval ...])
                  AC_DEFINE_UNQUOTED([ADLB_LOCATION],
                                     "$withval", [ADLB Location])
                  USE_ADLB=$withval
                 ],
                 [
                  AC_MSG_WARN([Not found: ADLB])
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
                  AC_MSG_WARN([Not found: MPI])
                  USE_MPI=0
                 ])
AC_SUBST(USE_MPI)

AC_ARG_WITH(tcl,
                 AS_HELP_STRING(
                 [--with-tcl],
                 [location of TCL]),
                 [AC_CHECK_FILE(${withval}/lib/tclConfig.sh,
                  [], [USE_TCL=0])
                  if test "$USE_TCL" = 0 ; then
                     AC_MSG_ERROR(Could not find TCL in $withval)
                  fi
                  AC_MSG_RESULT([using TCL in $withval ...])
                  AC_DEFINE_UNQUOTED([TCL_LOCATION],
                                     "$withval", [TCL Location])
                  USE_TCL=$withval
                  source $USE_TCL/lib/tclConfig.sh
                 ],
                 [
                  AC_MSG_WARN([Not found: TCL])
                  USE_TCL=0
                 ])
AC_SUBST(USE_TCL)
AC_SUBST(TCL_INCLUDE_SPEC)
AC_SUBST(TCL_LIB_SPEC)

AC_CONFIG_FILES(
        src/turbine/module.mk
        src/util/module.mk
        src/tcl/module.mk
        src/tcl/adlb/module.mk
        src/tcl/turbine/module.mk
        lib/module.mk
        scripts/turbine-config.sh
        test/module.mk
        test/runbin.zsh
	Makefile
)

AC_OUTPUT
