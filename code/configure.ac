
# Process this file with autoconf to produce a configure script.

# NOTE: Default CFLAGS are "-g -O2"
# Use ./configure CFLAGS="-g ..." to change the defaults

# CONVENTIONS:
# For a path name value,
# 0 means NOT SET
# NOTFOUND means FILE NOT FOUND

AC_PREREQ([2.62])
AC_INIT([Turbine], [0.1], [wozniak@mcs.anl.gov])
AC_CONFIG_SRCDIR([src/turbine/turbine.c])
AC_CONFIG_HEADERS([config.h])

# Checks for programs.
AC_PROG_CC
# We trust the user's install and omit AC_PROG_INSTALL

# Checks for headers
AC_CHECK_HEADERS([stddef.h sys/param.h sys/time.h])

# Checks for typedefs, structures, and compiler characteristics.
AC_C_INLINE
AC_HEADER_STDBOOL
AC_TYPE_PID_T
AC_TYPE_SIZE_T
AC_TYPE_UINT8_T
AC_TYPE_UINT16_T
AC_TYPE_UINT32_T

# Checks for library functions.
AC_FUNC_MALLOC
AC_CHECK_FUNCS([strchr strdup strnlen gettimeofday])

# Define templates
AC_DEFINE([HAVE_STDDEF_H], [], [Do we have stddef.h?])
AC_DEFINE([HAVE_SYS_PARAM_H], [], [Do we have sys/param.h?])

AC_ARG_WITH(c-utils,
                 AS_HELP_STRING(
                    [--with-c-utils],
                    [location of ExM c-utils]),
                   [
                    USE_C_UTILS=0
                    AC_CHECK_FILE(${withval}/include/c-utils.h,
                                  [USE_C_UTILS=1], [])
                    if test "$USE_C_UTILS" = 0 ; then
                       AC_MSG_ERROR([Could not find ExM c-utils in $withval])
                    fi
                    AC_MSG_RESULT([using ExM c-utils in $withval ...])
                    USE_C_UTILS=$withval
                 ],
                 [
                   AC_MSG_ERROR([Not found: ExM c-utils])
                   USE_C_UTILS=0
                 ])
AC_SUBST(USE_C_UTILS)

# Using the MPI library under Tcl apparently requires MPICH as a
# shared library

# MPI defaults (may be overridden on special systems)
USE_MACHINE=normal
USE_LAUNCHER=mpiexec
LIBMPI=libmpich.so

# Special systems:
# On the SiCortex, we use srun and libmpiP.a
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

# In custom, the user will optionally specify MPI directories below
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

AC_ARG_WITH(mpi,
                 AS_HELP_STRING(
                    [--with-mpi],
                    [location of MPI installation]),
                 [AC_CHECK_FILE(${withval}/lib/${LIBMPI},
                  [], [USE_MPI=0])
                  if test "$USE_MPI" = 0 ; then
                     AC_MSG_ERROR(Could not find MPI in $withval)
                  fi
                  AC_MSG_RESULT([using MPI in $withval])
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

# We assume the libmpe.so is in the ADLB directory
# See Turbine - Build for explanation
USE_MPE=0
AC_ARG_ENABLE(mpe,
     AS_HELP_STRING(
                    [--enable-mpe],
                    [enable Tcl MPE module]),
     [ [[ $enableval == yes ]] && USE_MPE=1 ], [] )
USE_MPE_WORD=no
(( $USE_MPE )) && USE_MPE_WORD=yes
AC_MSG_RESULT([MPE enabled? $USE_MPE_WORD ])
AC_SUBST(USE_MPE)
AC_DEFINE_UNQUOTED(USE_MPE,$USE_MPE,[Enables MPE])

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
                  USE_ADLB=$withval
                 ],
                 [
                  AC_MSG_WARN([Not found: ADLB])
                  USE_ADLB=0
                 ])
AC_SUBST(USE_ADLB)

AC_ARG_WITH(tcl,
                 AS_HELP_STRING(
                    [--with-tcl],
                    [location of Tcl]),
                 [AC_CHECK_FILE(${withval}/lib/tclConfig.sh,
                  [], [USE_TCL=0])
                  if test "$USE_TCL" = 0 ; then
                     AC_MSG_ERROR(Could not find Tcl in $withval)
                  fi
                  AC_MSG_RESULT([using Tcl in $withval])
                  USE_TCL=$withval
                  source $USE_TCL/lib/tclConfig.sh
                 ],
                 [
                  AC_MSG_WARN([Not found: Tcl])
                  USE_TCL=0
                 ])
AC_SUBST(USE_TCL)
AC_SUBST(TCL_INCLUDE_SPEC)
AC_SUBST(TCL_LIB_SPEC)

DISABLE_LOG=0
AC_ARG_ENABLE(log,
                AS_HELP_STRING(
                   [--disable-log],
                   [Disables logging]),
                 [
                 if test "$enableval" = no ; then
                   DISABLE_LOG=1
                 fi
                 ],
                 [])
AC_DEFINE_UNQUOTED(DISABLE_LOG,$DISABLE_LOG,[Disables logging])

AC_CONFIG_FILES(
        src/turbine/module.mk
        src/util/module.mk
        src/tcl/module.mk
        src/tcl/adlb/module.mk
        src/tcl/turbine/module.mk
        src/tcl/mpe/module.mk
        lib/module.mk
        scripts/turbine-config.sh
        tests/module.mk
        tests/runbin.zsh
	Makefile
)

AC_OUTPUT
