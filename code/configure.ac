
# Process this file with autoconf to produce a configure script.

# NOTE: Default CFLAGS are "-g -O2"
# Use ./configure CFLAGS="-g ..." to change the defaults
#     or --enable-fast

# CONVENTIONS:
# For a path name value,
# 0 means NOT SET
# NOTFOUND means FILE NOT FOUND

AC_PREREQ([2.62])
AC_INIT([Turbine], [0.1], [wozniak@mcs.anl.gov])
AC_CONFIG_SRCDIR([src/turbine/turbine.c])
AC_CONFIG_HEADERS([config.h])

TURBINE_VERSION=$( cat version.txt )
AC_MSG_RESULT([Turbine version: ${TURBINE_VERSION}])
AC_SUBST(TURBINE_VERSION)

[[ ${prefix} != ${PWD} ]] ||
  AC_MSG_ERROR([Your --prefix should not be the same as PWD: $PWD])

# Check OS
USE_MAC="no"
if [[ $( uname ) == "Darwin" ]]
then
        AC_MSG_RESULT([detected Mac.])
        USE_MAC="yes"
fi
AC_SUBST(USE_MAC)

# Checks for programs.
AC_PROG_CC
# We trust the user's install and omit AC_PROG_INSTALL

# We prefer to use cp -u for installation
AC_CACHE_CHECK([for cp that supports -u], [ac_cv_path_cp_u],
            [AC_PATH_PROGS_FEATURE_CHECK([CP_U], [cp],
              [[cp_u_out=`cp -u /dev/null /dev/stdout 2>&1 | cat > /dev/null`
                test "$?" = 0 \
                && ac_cv_path_cp_u=yes || ac_cv_path_cp_u=no ]],
              [])])
AC_SUBST([CP_U], [$ac_cv_path_cp_u])

# We prefer to use cp --parents for installation
AC_CACHE_CHECK([for cp that supports --parents], [ac_cv_path_cp_parents],
            [AC_PATH_PROGS_FEATURE_CHECK([CP_PARENTS], [cp],
              [[cp_parents_out=`cp --parents /dev/null . > /dev/null 2>&1`
                test "$?" = 0 \
                && ac_cv_path_cp_parents=yes || ac_cv_path_cp_parents=no ]],
              [])])
# If cp --parents succeeded, we have a directory to remove
[[ -d dev ]] && rm -r dev
AC_SUBST([CP_PARENTS], [$ac_cv_path_cp_parents])

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
AC_CHECK_FUNCS([gettimeofday memset strchr strdup strlen strnlen])

# Define templates
AC_DEFINE([HAVE_STDDEF_H], [], [Do we have stddef.h?])
AC_DEFINE([HAVE_SYS_PARAM_H], [], [Do we have sys/param.h?])

AC_ARG_WITH(c-utils,
                 AS_HELP_STRING(
                    [--with-c-utils],
                    [location of ExM c-utils]),
                   [
                    USE_C_UTILS=0
                    AC_MSG_CHECKING([${withval}/include/c-utils.h])
                    [[ -r ${withval}/include/c-utils.h ]] && USE_C_UTILS=1
                    if [[ ${USE_C_UTILS} == 0 ]]
                    then
                       AC_MSG_ERROR([Could not find ExM c-utils in $withval])
                    fi
                    AC_MSG_RESULT([yes])
                    USE_C_UTILS=$withval
                 ],
                 [
                   AC_MSG_ERROR([Not found: ExM c-utils])
                   USE_C_UTILS=0
                 ])
AC_SUBST(USE_C_UTILS)

# Using the MPI library under Tcl apparently requires MPICH as a
# shared library

# There are many ways to set up MPI: make sure the user picks one
MPI_SET=0

# MPI defaults (may be overridden on special systems)
USE_MACHINE=normal
USE_LAUNCHER=mpiexec
MPI_LIB_NAME=mpich

# Special systems:
# On the SiCortex, we use srun and libmpiP.a
AC_ARG_ENABLE(sicortex,
                 AS_HELP_STRING(
                    [--enable-sicortex],
                    [Required if running on SiCortex]),
                 [
                        USE_MACHINE=sicortex
                        USE_LAUNCHER=srun
                        MPI_LIB_NAME=libmpiP.a
                        MPI_SET=1
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
                        MPI_SET=1
                 ],
                 [])

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
                    [directory containing MPI lib (usually libmpich)]),
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

SO_SUFFIX=so
if test `uname` = Darwin; then
   SO_SUFFIX=dylib
fi
AC_ARG_WITH(mpi-lib-name,
                 AS_HELP_STRING(
                    [--with-mpi-lib-name],
                    [name of MPI lib (usually mpich) -
                        assumes mpi-lib-dir has been given]),
                 [
                  MPI_LIB_NAME=${withval}
                  FILE=${USE_MPI_LIB_DIR}/lib${MPI_LIB_NAME}.${SO_SUFFIX}
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

USE_MPI=1
AC_ARG_WITH(mpi,
    AS_HELP_STRING(
        [--with-mpi],
        [location of MPI installation]),
    [
        if [[ ! -f ${withval}/lib/lib${MPI_LIB_NAME}.${SO_SUFFIX} ]]
        then
            USE_MPI=0
        fi
        if [[ ${USE_MPI} == 0 ]]
        then
            AC_MSG_ERROR(Could not find MPI in $withval)
        fi
        AC_MSG_RESULT([using MPI in $withval])
        MPI_SET=1
        USE_MPI=$withval
        USE_LAUNCHER=${USE_MPI}/bin/mpiexec
    ],
    [
        USE_MPI=0
    ]
)
AC_SUBST(USE_MPI)

(( ${MPI_SET} )) || AC_MSG_ERROR([Insufficient MPI settings!])

# Discover MPI implementation:
# MPI_IMPL will be unknown, OpenMPI, or MPICH2
MPI_IMPL=unknown
if [[ ${USE_MPI_INCLUDE} != 0 ]]
then
   MPI_H=${USE_MPI_INCLUDE}/mpi.h
else
   MPI_H=${USE_MPI}/include/mpi.h
fi
AC_MSG_CHECKING([MPI implementation in ${MPI_H} ])
if grep -q OMPI_MPI_H ${MPI_H} > /dev/null 2>&1
then
   OMPI_MINOR_VERSION=$( grep OMPI_MINOR_VERSION ${MPI_H} | \
                         head -1 | cut -d ' ' -f 3 )
   MPI_IMPL=OpenMPI_1.${OMPI_MINOR_VERSION}
elif grep -q MPICH_NAME ${MPI_H} > /dev/null 2>&1
then
   MPI_IMPL=MPICH2
fi
AC_MSG_RESULT([${MPI_IMPL}])
AC_SUBST(MPI_IMPL)

AC_SUBST(USE_MACHINE)
AC_SUBST(USE_LAUNCHER)

# We assume the libmpe.so is in the ADLB directory
# See Turbine - Build for explanation
ENABLE_MPE=0
USE_MPE=0
AC_ARG_WITH(mpe,
     AS_HELP_STRING(
                    [--with-mpe],
                    [Enable Turbine MPE, set path to libmpe.so]),
     [ USE_MPE=${withval} ], [] )
if [[ ${USE_MPE} != 0 ]]
then
    AC_MSG_RESULT([Enabling MPE]);
    AC_CHECK_FILE(${USE_MPE}/libmpe.so)
    AC_DEFINE_UNQUOTED(ENABLE_MPE,ENABLE_MPE,[Enables MPE])
fi
AC_SUBST(USE_MPE)
AC_SUBST(ENABLE_MPE)

USE_ADLB=1
AC_ARG_WITH(adlb,
    AS_HELP_STRING(
        [--with-adlb],
        [location of ADLB]),
    [
        AC_MSG_CHECKING([for ${withval}/include/adlb.h])
        if [[ ! -r ${withval}/include/adlb.h ]]
        then
            USE_ADLB=0
        fi
        if [[ ${USE_ADLB} == 0 ]]
        then
            AC_MSG_ERROR(Could not find ADLB in $withval)
        fi
        AC_MSG_RESULT(ok)
        USE_ADLB=$withval
    ],
    [
        AC_MSG_WARN([Not found: ADLB])
        USE_ADLB=0
    ]
)
AC_SUBST(USE_ADLB)

# tclsh is used in the build process (cf. debug-auto.tcl)
# Allow user to specify a native tclsh for use when cross-compiling
# The user has not yet provided tclsh-local (default to USE_TCLSH):
USE_TCLSH_LOCAL=0
AC_ARG_WITH(tclsh-local,
    AS_HELP_STRING(
        [--with-tclsh-local],
        [name of tclsh compatible with build system]),
        [
            AC_MSG_CHECKING([for Tcl local executable in: ${withval} ])
            USE_TCLSH_LOCAL=$( maint/find-tcl.zsh ${withval} )
            if [[ ${?} != 0 ]]
            then
                AC_MSG_ERROR([failed!])
            fi
            AC_MSG_RESULT($(basename ${USE_TCLSH_LOCAL}))
        ],
        [])

USE_TCL=unknown
USE_TCL_LIB_DIR=0
AC_ARG_WITH(tcl,
    AS_HELP_STRING(
        [--with-tcl],
        [location of Tcl]),
    [
        AC_MSG_CHECKING([for ${withval}/lib/tclConfig.sh ])
        if [[ ! -r ${withval}/lib/tclConfig.sh ]]
        then
            USE_TCL=0
        fi
        if [[ ${USE_TCL} == 0 ]]
        then
            AC_MSG_ERROR(Could not find Tcl in $withval)
        fi
        AC_MSG_RESULT([ok])
        USE_TCL=$withval
        source $USE_TCL/lib/tclConfig.sh
    ],
    [
        AC_MSG_WARN([Not given: Tcl])
        USE_TCL=0
    ]
)
USE_TCL_LIB_DIR=${USE_TCL}/lib

if [[ ${USE_TCLSH_LOCAL} == 0 ]]
then
    USE_TCLSH=$( maint/find-tcl.zsh ${USE_TCL} )
    if [[ ${?} != 0 ]]
    then
        AC_MSG_ERROR([Could not find Tcl 8.5 binary!])
    fi
    USE_TCLSH_LOCAL=${USE_TCLSH}
else
    export NO_RUN=1
    USE_TCLSH=$( maint/find-tcl.zsh ${USE_TCL} )
    if [[ ${?} != 0 ]]
    then
        AC_MSG_ERROR([Could not find Tcl 8.5 binary!])
    fi
fi
AC_MSG_RESULT([using Turbine Tcl executable: ${USE_TCLSH}])
AC_SUBST(USE_TCL)
AC_SUBST(USE_TCL_LIB_DIR)
AC_SUBST(USE_TCLSH)
AC_SUBST(USE_TCLSH_LOCAL)

# Now, allow user to override Tcl include

AC_ARG_WITH(tcl-include,
            AS_HELP_STRING(
               [--with-tcl-include],
               [directory containing tcl.h]),
            [AC_CHECK_FILE(${withval}/tcl.h,[],[FAIL=1])
             [[ ${FAIL} == 1 ]] &&
               AC_MSG_ERROR(Could not find tcl.h in ${withval})
             AC_MSG_RESULT([using Tcl include: ${withval}/tcl.h])
             TCL_INCLUDE_SPEC=-I${withval}
            ],
            [])

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
# DISABLE_LOG set below:

ENABLE_FAST=
AC_ARG_ENABLE(fast,
     [  --enable-fast         Enable NDEBUG, -O3, disable log. ],
        ENABLE_FAST=1
        DISABLE_LOG=1
        CFLAGS="${CFLAGS} -O3 -DNDEBUG")
AC_SUBST(ENABLE_FAST)
AC_SUBST(CFLAGS)

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
