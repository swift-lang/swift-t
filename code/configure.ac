
# Process this file with autoconf to produce a configure script.

# NOTE: Default CFLAGS are "-g -O2"
# Use ./configure CFLAGS="-g ..." to change the defaults

# CONVENTIONS:
# For a path name value,
# 0 means NOT SET
# NOTFOUND means FILE NOT FOUND

AC_PREREQ([2.63])
AC_INIT([FULL-PACKAGE-NAME], [VERSION], [BUG-REPORT-ADDRESS])
AC_CONFIG_SRCDIR([src/turbine/turbine.c])
AC_CONFIG_HEADERS([config.h])

# Checks for programs.
AC_PROG_CC
# This does not work:
AC_PROG_INSTALL

# Checks for headers
AC_CHECK_HEADERS([stddef.h,sys/param.h,sys/time.h])

# Checks for typedefs, structures, and compiler characteristics.
AC_C_INLINE
AC_HEADER_STDBOOL
AC_TYPE_SIZE_T
AC_TYPE_UINT8_T
AC_TYPE_UINT16_T
AC_TYPE_UINT32_T

# Checks for library functions.
AC_FUNC_MALLOC
AC_CHECK_FUNCS([stddef,strchr,strdup,gettimeofday])
AC_CHECK_FUNCS([sys/param.h,sys/time.h])

# Define templates
AC_DEFINE([HAVE_STDDEF_H], [], [Do we have stddef.h?])
AC_DEFINE([HAVE_SYS_PARAM_H], [], [Do we have sys/param.h?])
AC_DEFINE([HAVE_PYTHON], [], [Do we have Python?])

# Special systems:
# On the SiCortex, we use srun and libmpiP.a
USE_MACHINE=normal
USE_LAUNCHER=mpiexec
LIBMPI=libmpich.so
AC_ARG_ENABLE(sicortex,
                 AS_HELP_STRING(
                 [--enable-sicortex],
                 [Required if running on SiCortex]),
                 [
                        USE_MACHINE=sicortex
                        USE_LAUNCHER=srun
                        LIBMPI=libmpiP.a
                 ],
                 [])

# On a custom system, the user will specify MPI directories below
AC_ARG_ENABLE(custom,
                 AS_HELP_STRING(
                 [--enable-custom],
                 [Select MPI include and lib separately]),
                 [
                        USE_MACHINE=custom
                        USE_LAUNCHER=mpiexec
                 ],
                 [])
AC_SUBST(USE_MACHINE)
AC_SUBST(USE_LAUNCHER)

AC_ARG_WITH(adlb,
                 AS_HELP_STRING(
                 [--with-adlb],
                 [location of ADLB]),
                 [AC_CHECK_FILE(${withval}/libadlb.so,
                  [], [USE_ADLB=0])
                  if test "$USE_ADLB" = 0 ; then
                     AC_MSG_ERROR(Could not find ADLB in $withval)
                  fi
                  AC_MSG_RESULT([using ADLB in $withval])
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
                 [location of MPI installation with mpicc]),
                 [AC_CHECK_FILE(${withval}/lib/${LIBMPI},
                  [], [USE_MPI=0])
                  if test "$USE_MPI" = 0 ; then
                     AC_MSG_ERROR(Could not find MPI in $withval)
                  fi
                  AC_MSG_RESULT([using MPICC in $withval])
                  AC_DEFINE_UNQUOTED([MPI_LOCATION],
                                     "$withval", [MPI Location])
                  USE_MPI=$withval
                 ],
                 [
                  USE_MPI=0
                 ])
AC_SUBST(USE_MPI)

# Defaults: do not use custom MPI include, lib locations
USE_MPI_INCLUDE=0
USE_MPI_LIB_DIR=0

# This is an MPI installation without mpicc (e.g., Cray)
AC_ARG_WITH(mpi-dir,
                 AS_HELP_STRING(
                 [--with-mpi-dir],
                 [location of MPI installation]),
                 [AC_CHECK_FILE(${withval}/include/mpi.h,
                  [], [USE_MPI_INCLUDE=NOTFOUND])
                  if test "$USE_MPI_INCLUDE" = NOTFOUND ; then
                     AC_MSG_ERROR(Could not find mpi.h
                                  in $withval/include)
                  fi
                  AC_MSG_RESULT([using MPI in $withval])
                  USE_MPI_INCLUDE=$withval/include
                  USE_MPI_LIB_DIR=$withval/lib
                 ],
                 [])
# AC_SUBST commands below

AC_ARG_WITH(mpi-include,
                 AS_HELP_STRING(
                 [--with-mpi-include],
                 [location of MPI header (mpi.h)]),
                 [AC_CHECK_FILE(${withval}/mpi.h,
                  [], [USE_MPI_INCLUDE=NOTFOUND])
                  if test "$USE_MPI_INCLUDE" = NOTFOUND ; then
                     AC_MSG_ERROR(Could not find mpi.h in $withval)
                  fi
                  AC_MSG_RESULT([using mpi.h in $withval])
                  USE_MPI_INCLUDE=$withval
                 ],
                 [])
AC_SUBST(USE_MPI_INCLUDE)

AC_ARG_WITH(mpi-lib-dir,
                 AS_HELP_STRING(
                 [--with-mpi-lib-dir],
                 [directory containing MPI lib (usually libmpich.a)]),
                 [AC_CHECK_FILE(${withval},
                  [], [USE_MPI_LIB_DIR=NOTFOUND])
                  if test "$USE_MPI_LIB_DIR" = NOTFOUND ; then
                     AC_MSG_ERROR(Could not find directory $withval)
                  fi
                  AC_MSG_RESULT([using MPI lib directory $withval])
                  USE_MPI_LIB_DIR=$withval
                 ],
                 [])
AC_SUBST(USE_MPI_LIB_DIR)

# Default: do not use custom MPI lib name
USE_MPI_LIB_NAME=0

AC_ARG_WITH(mpi-lib-name,
                 AS_HELP_STRING(
                 [--with-mpi-lib-name],
                 [name of MPI lib (usually mpich) -
                  assumes mpi-lib-dir has been given]),
                 [
                  FILE=${USE_MPI_LIB_DIR}/lib${withval}.a
                  AC_CHECK_FILE(${FILE},
                  [], [USE_MPI_LIB_NAME=NOTFOUND])
                  if test "$USE_MPI_LIB_NAME" = NOTFOUND ; then
                     AC_MSG_ERROR(Could not find ${FILE})
                  fi
                  AC_MSG_RESULT([using MPI lib $FILE])
                  USE_MPI_LIB_NAME=$withval
                 ],
                 [
                  if test "$USE_MPI_LIB_DIR" != 0; then
                    FILE=${USE_MPI_LIB_DIR}/libmpich.a
                    AC_CHECK_FILE(${FILE},
                    [], [USE_MPI_LIB_NAME=NOTFOUND])
                    if test "$USE_MPI_LIB_NAME" = NOTFOUND ; then
                       AC_MSG_ERROR(Could not find ${FILE})
                    fi
                    AC_MSG_RESULT([using MPI lib $FILE])
                    USE_MPI_LIB_NAME=mpich
                  fi
                 ])
AC_SUBST(USE_MPI_LIB_NAME)

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

AC_ARG_WITH(python,
                 AS_HELP_STRING(
                 [--with-python],
                 [location of Python]),
                 [AC_CHECK_FILE(${withval}/bin/python,
                  [], [USE_PYTHON=0])
                  if test "$USE_PYTHON" = 0 ; then
                     AC_MSG_ERROR(Could not find python in $withval)
                  fi
                  AC_MSG_RESULT([using python in $withval ...])
                  AC_DEFINE_UNQUOTED([PYTHON_LOCATION],
                                     "$withval", [Python location])
                  USE_PYTHON=$withval
                  source $USE_PYTHON/lib/tclConfig.sh
                 ],
                 [
                  AC_MSG_RESULT([Not using Python.])
                  USE_PYTHON=0
                 ])
AC_SUBST(USE_PYTHON)

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

if test ! "$USE_PYTHON" = 0 ; then
AC_CONFIG_FILES(
        src/py/adlb/module.mk
)
fi

AC_OUTPUT
