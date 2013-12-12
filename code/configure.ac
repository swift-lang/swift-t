
# TURBINE CONFIGURE.AC

# Process this file with autoconf to produce a configure script.

# NOTE: Default CFLAGS are "-g -O2"
# Use ./configure CFLAGS="-g ..." to change the defaults
#     or --enable-fast

# CONVENTIONS:
# For a path name value,
# 0 means NOT SET
# NOTFOUND means FILE NOT FOUND

define([turbine_version],
       regexp(esyscmd(cat version.txt),[\([.0-9]*\)],[\1]))

AC_PREREQ([2.62])
AC_INIT([Turbine], [turbine_version()], [wozniak@mcs.anl.gov])
AC_CONFIG_SRCDIR([src/turbine/turbine.c])
AC_CONFIG_HEADERS([config.h])

TURBINE_VERSION=turbine_version()
AC_MSG_RESULT([Turbine version: ${TURBINE_VERSION}])
AC_SUBST(TURBINE_VERSION)

if [[ ${prefix} == "NONE" ]]
then
  AC_MSG_ERROR("You must provide --prefix")
fi

echo "PREFIX: ${prefix}"
SOFTWARE=$( cd $(dirname ${prefix}) && /bin/pwd )
if [[ ${?} != 0 ]]
then
  SOFTWARE=0
fi
echo "SOFTWARE: ${SOFTWARE}"

if [[ ${prefix} == ${PWD} ]]
then
  AC_MSG_ERROR([Your --prefix should not be the same as PWD: $PWD])
fi

echo ${PWD} > source.txt

# Check OS
USE_MAC="no"
if [[ $( uname ) == "Darwin" ]]
then
        AC_MSG_RESULT([detected Mac.])
        USE_MAC="yes"
fi
AC_SUBST(USE_MAC)

# Checks for programs
AC_PROG_CC
AC_PATH_PROG([SWIG],[swig],[0])
[[ ${SWIG} == 0 ]] && AC_MSG_ERROR([Could not find SWIG!])
# We trust the user's install and omit AC_PROG_INSTALL

# We prefer to use cp -u for installation
AC_CACHE_CHECK([for cp that supports -u], [ac_cv_path_cp_u],
            [AC_PATH_PROGS_FEATURE_CHECK([CP_U], [cp],
              [[cp_u_out=`cp -u /dev/null cp_u.token 2>&1 > /dev/null`
                test "$?" = 0 \
                && ac_cv_path_cp_u=yes || ac_cv_path_cp_u=no ]],
              [])])
AC_SUBST([CP_U], [$ac_cv_path_cp_u])
[[ -r cp_u.token ]] && rm cp_u.token

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
AC_CHECK_HEADERS([fcntl.h limits.h malloc.h stddef.h sys/param.h])
AC_CHECK_HEADERS([sys/time.h])

# Checks for typedefs, structures, and compiler characteristics.
AC_C_INLINE
AC_HEADER_STDBOOL
AC_TYPE_PID_T
AC_TYPE_SIZE_T
AC_TYPE_INT32_T
AC_TYPE_INT64_T
AC_TYPE_MODE_T
AC_TYPE_UINT8_T
AC_TYPE_UINT16_T
AC_TYPE_UINT32_T
AC_CHECK_TYPES([ptrdiff_t])

# Checks for library functions.
# Although autoscan 2.68 warns for AC_FUNC_STRNLEN, we do not use it
# because of known autoconf issues.  We do check for strnlen here:
AC_FUNC_FORK
AC_FUNC_MALLOC
AC_FUNC_REALLOC
AC_CHECK_FUNCS([gettimeofday memset pow setenv strchr strdup strlen])
AC_CHECK_FUNCS([strnlen strstr strtol strtoul uname])

# Define templates
AC_DEFINE([HAVE_STDDEF_H], [], [Do we have stddef.h?])
AC_DEFINE([HAVE_SYS_PARAM_H], [], [Do we have sys/param.h?])

USE_C_UTILS=0
AC_ARG_WITH(c-utils,
    AS_HELP_STRING([--with-c-utils], [location of ExM c-utils]),
    [
        AC_MSG_CHECKING([${withval}/include/c-utils.h])
        [[ -r ${withval}/include/c-utils.h ]] && USE_C_UTILS=1
        if [[ ${USE_C_UTILS} == 0 ]]
        then
            AC_MSG_ERROR([Could not find ExM c-utils in $withval])
        fi
        AC_MSG_RESULT([yes])
        USE_C_UTILS=${withval}
    ],
    [
        dnl If not given, look in SOFTWARE, if valid
        if [[ ${SOFTWARE} == 0 ]]
        then
            AC_MSG_ERROR([Not found: ExM c-utils])
        fi
        location=${SOFTWARE}/c-utils
        AC_CHECK_FILE(${location}/include/c-utils.h,
                      [USE_C_UTILS=1], [])
        if [[ ${USE_C_UTILS} == 1 ]]
        then
            USE_C_UTILS=${location}
        else
            AC_MSG_ERROR([Not found: ExM c-utils])
        fi
    ]
)
AC_SUBST(USE_C_UTILS)

# MPI defaults (may be overridden on special systems)
USE_MACHINE=normal
# The "launcher" is used by job submission systems to launch jobs
# Usually a path to mpiexec, srun, or aprun
# Full path to launcher (optional):
USE_LAUNCHER=mpiexec
USE_MPI_DIR=0
USE_MPI_INCLUDE=0
USE_MPI_LIB_DIR=0
USE_MPI_LIB_NAME=mpich
ENABLE_CUSTOM_MPI=0

# Set shared object suffix (.so on Linux, .dylib on Mac)
SO_SUFFIX=so
if [[ $(uname) = "Darwin" ]]
then
   SO_SUFFIX=dylib
fi

# Set MPI_DIR directly
AC_ARG_WITH(mpi,
    AS_HELP_STRING([--with-mpi], [location of MPI installation]),
    [
        if [[ ! -d ${withval} ]]
        then
            AC_MSG_ERROR(No MPI directory at $withval)
        fi
        USE_MPI_DIR=${withval}
        USE_MPI_INCLUDE=${USE_MPI_DIR}/include
        USE_MPI_LIB_DIR=${USE_MPI_DIR}/lib
        AC_MSG_RESULT([Trying MPI directory: ${USE_MPI_DIR}])
    ],
    [])

# In custom-mpi, the user will optionally specify MPI directories below
AC_ARG_ENABLE(custom-mpi,
    AS_HELP_STRING([--enable-custom-mpi],
                   [Select MPI include and lib separately]),
    [
        ENABLE_CUSTOM_MPI=1
        USE_MACHINE=custom
        USE_LAUNCHER=mpiexec
    ],
    [])

# Special systems:
# On the SiCortex, we use srun and libmpiP.a
AC_ARG_ENABLE(sicortex,
    AS_HELP_STRING([--enable-sicortex],
                   [Required if running on SiCortex]),
    [
        USE_MACHINE=sicortex
        USE_LAUNCHER=srun
        USE_MPI_DIR=/usr
        USE_MPI_LIB_NAME=scmpi
    ],
    [])

AC_ARG_WITH(launcher,
    AS_HELP_STRING([--with-launcher],
                   [name in PATH or full path to job launcher
                       (mpiexec, etc.)]),
    [
        USE_LAUNCHER=${withval}
        AC_CHECK_FILE(${USE_LAUNCHER}, [],
                      AC_MSG_ERROR("Launcher not found!"))
    ])

# Set MPI_DIR with respect to an mpicc in PATH
if [[ ${USE_MPI_DIR} == 0 ]]
then
   # Last chance to configure MPI: look for mpiexec in PATH
   program=$( which mpicc )
   if [[ ${?} == 0 ]]
   then
       AC_MSG_RESULT([PATH contains: ${program}])
       USE_MPI_DIR=$( cd $(dirname ${program})/.. ; /bin/pwd )
       USE_MPI_INCLUDE=${USE_MPI_DIR}/include
       USE_MPI_LIB_DIR=${USE_MPI_DIR}/lib
       AC_MSG_RESULT([Trying MPI directory: ${USE_MPI_DIR}])
   elif (( ! ${ENABLE_CUSTOM} ))
   then
       AC_MSG_ERROR([Turbine requires --with-mpi or
                                  --enable-custom-mpi or
                                  or mpicc in PATH .])
   fi
fi

# Look for mpi.h at its APT location
if [[ ! -f ${MPI_INCLUDE}/mpi.h ]]
then
    if [[ -f ${MPI_INCLUDE}/mpi/mpi.h ]]
    then
        MPI_INCLUDE=${MPI_INCLUDE}/mpi
    fi
fi

AC_ARG_WITH(mpi-include,
    AS_HELP_STRING([--with-mpi-include],
                   [directory containing MPI header (mpi.h)]),
    [
        USE_MPI_INCLUDE=${withval}
    ],
    [])

AC_ARG_WITH(mpi-lib-dir,
    AS_HELP_STRING([--with-mpi-lib-dir],
                   [directory containing MPI lib (usually libmpich)]),
    [
        USE_MPI_LIB_DIR=${withval}
    ],
    [])

AC_ARG_WITH(mpi-lib-name,
    AS_HELP_STRING([--with-mpi-lib-name],
                   [name of MPI lib (usually mpich) -
                    assumes mpi-lib-dir has been given]),
    [
        USE_MPI_LIB_NAME=${withval}
    ],
    [])

# Now check MPI settings

AC_CHECK_FILE(${USE_MPI_LIB_DIR},
              [], [AC_MSG_ERROR(Could not find directory ${withval})])

AC_CHECK_FILE(${USE_MPI_INCLUDE}/mpi.h,
              [],
              [AC_MSG_ERROR([Could not find mpi.h in ${withval}])])

MPI_LIB=${USE_MPI_LIB_DIR}/lib${USE_MPI_LIB_NAME}.${SO_SUFFIX}
AC_CHECK_FILE(${MPI_LIB},
              [], AC_MSG_ERROR([Could not find MPI shared library:
                               ${MPI_LIB}.
                               MPI may not be installed at this
                               location or shared library may not
                               have been built.]))

AC_MSG_RESULT([Using MPI launcher: ${USE_LAUNCHER}])

# Write out MPI settings
AC_SUBST(USE_MACHINE)
AC_SUBST(USE_LAUNCHER)
AC_SUBST(USE_MPI_INCLUDE)
AC_SUBST(USE_MPI_LIB_DIR)
AC_SUBST(USE_MPI_LIB_NAME)

# Discover MPI implementation:
# MPI_IMPL will be unknown, OpenMPI, or MPICH
MPI_IMPL=unknown
if [[ ${USE_MPI_INCLUDE} != 0 ]]
then
   MPI_H=${USE_MPI_INCLUDE}/mpi.h
else
   MPI_H=${USE_MPI_DIR}/include/mpi.h
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

# We assume libmpe.so is in the MPE/lib directory
# See Turbine - Build for explanation
ENABLE_MPE=0
USE_MPE=0
LIBMPE=libmpe.${SO_SUFFIX}
AC_ARG_WITH(mpe,
     AS_HELP_STRING(
                    [--with-mpe],
                    [Enable Turbine MPE, set path to ${LIBMPE}]),
     [ USE_MPE=${withval} ; ENABLE_MPE=1], [:] )
if [[ ${USE_MPE} != 0 ]]
then
    AC_MSG_RESULT([Enabling MPE]);
    AC_CHECK_FILE(${USE_MPE}/lib/${LIBMPE},,
                  AC_MSG_ERROR([Could not find MPE!]))
    AC_DEFINE_UNQUOTED(ENABLE_MPE,ENABLE_MPE,[Enables MPE])
fi
AC_SUBST(USE_MPE)
AC_SUBST(ENABLE_MPE)

ENABLE_XPT=yes
AC_ARG_ENABLE(checkpoint,
     AS_HELP_STRING(
                    [--enable-checkpoint],
                    [Enable Turbine support for ADLB checkpointing (enabled by default)]), [
      if [[ "$enableval" == yes ]]
      then
        ENABLE_XPT=yes
      elif [[ "$enableval" == no ]]
      then
        ENABLE_XPT=no
      else
        echo "Invalid enable-checkpoint value: $enableval"
        exit 1
      fi
    ], [])
if [[ ${ENABLE_XPT} == yes ]]
then
    AC_MSG_RESULT([Checkpointing enabled]);
else
    AC_MSG_RESULT([Checkpointing disabled]);
fi
AC_SUBST(ENABLE_XPT)

USE_ADLB=0
AC_ARG_WITH(adlb,
    AS_HELP_STRING(
        [--with-adlb],
        [location of ADLB]),
    [
        AC_MSG_CHECKING([for ${withval}/include/adlb.h])
        if [[ ! -r ${withval}/include/adlb.h ]]
        then
            AC_MSG_ERROR(Could not find ADLB in $withval)
        fi
        AC_MSG_RESULT(ok)
        USE_ADLB=${withval}
    ],
    [
        dnl If not given, look in SOFTWARE, if valid
        if [[ ${SOFTWARE} == 0 ]]
        then
            AC_MSG_ERROR([Not found: ADLB])
        fi
        location=${SOFTWARE}/lb
        AC_CHECK_FILE(${location}/include/adlb.h,
                      [USE_ADLB=${location}], [])
        if [[ ${USE_ADLB} == 0 ]]
        then
            AC_MSG_ERROR([Not found: ADLB])
        fi
    ]
)
AC_SUBST(USE_ADLB)

# Python is optional
HAVE_PYTHON=0
AC_ARG_ENABLE(python,
    AS_HELP_STRING([--enable-python],
                   [Enable calling python]),
    [
      HAVE_PYTHON=1
    ])

# By default, use default location
USE_PYTHON=0
USE_PYTHON_INCLUDE=0
USE_PYTHON_LIB=0
USE_PYTHON_NAME=python2.7

AC_ARG_WITH(python,
   AS_HELP_STRING([--with-python],
                   [Use this python directory]),
  [
      HAVE_PYTHON=1
      USE_PYTHON=${withval}
      AC_CHECK_FILE(${USE_PYTHON}/include/python2.7/Python.h, [],
                    [AC_MSG_ERROR([Could not find python header!])])
      USE_PYTHON_INCLUDE=${USE_PYTHON}/include
      USE_PYTHON_LIBDIR=${USE_PYTHON}/lib
      USE_PYTHON_NAME=libpython2.7.so
  ])

AC_ARG_WITH(python-lib,
    AS_HELP_STRING([--with-python-lib],
                   [Use this python directory library -
                    provide fully qualified path]),
    [
      HAVE_PYTHON=1
      USE_PYTHON=1
      USE_PYTHON_LIB=${withval}
      AC_CHECK_FILE(${USE_PYTHON_LIB}, [],
                    [AC_MSG_ERROR([Could not find python!])])
      USE_PYTHON_LIBDIR=$( dirname ${USE_PYTHON} )
      USE_PYTHON_NAME=$( basename ${USE_PYTHON} )
    ])

if [[ ${USE_PYTHON} != 0 ]]
then
    USE_PYTHON_NAME=${USE_PYTHON_NAME#lib}
    USE_PYTHON_NAME=${USE_PYTHON_NAME%.so}
    echo "Using Python lib directory: ${USE_PYTHON_LIBDIR}"
    echo "Using Python lib name:      ${USE_PYTHON_NAME}"
fi

AC_DEFINE_UNQUOTED([HAVE_PYTHON],$HAVE_PYTHON,[Enables Python])
AC_SUBST(HAVE_PYTHON)
AC_SUBST(USE_PYTHON_INCLUDE)
AC_SUBST(USE_PYTHON_LIBDIR)
AC_SUBST(USE_PYTHON_NAME)

if (( HAVE_PYTHON ))
then
    echo "Python enabled"
else
    echo "Python disabled"
fi

# R is optional
HAVE_R=0
USE_R=0
AC_ARG_ENABLE(r,
    AS_HELP_STRING([--enable-r],
                   [Enable calling R language]),
    [
      HAVE_R=1
      USE_R=/usr/share/R
    ])

AC_ARG_WITH(r,
   AS_HELP_STRING([--with-r],
                   [Use this R directory]),
  [
      HAVE_R=1
      USE_R=${withval}
      AC_CHECK_FILE(${USE_R}/include/R.h, [],
                    [AC_MSG_ERROR([Could not find R header!])])
  ])

AC_DEFINE_UNQUOTED([HAVE_R],$HAVE_R,[Enables R])
AC_SUBST(HAVE_R)
AC_SUBST(USE_R)

if (( HAVE_R ))
then
    echo "R enabled"
else
    echo "R disabled"
fi

# tclsh is used in the build process (cf. debug-auto.tcl)
# Allow user to specify a native tclsh for use when cross-compiling
# The user has not yet provided tclsh-local (default to USE_TCLSH):
USE_TCLSH_LOCAL=0
AC_ARG_WITH(tclsh-local,
    AS_HELP_STRING(
        [--with-tclsh-local],
        [name of tclsh compatible with build system]),
    [
        AC_MSG_CHECKING([for Tcl local executable in: ${withval}])
        USE_TCLSH_LOCAL=$( maint/find-tcl.zsh ${withval} )
        if [[ ${?} != 0 ]]
        then
            AC_MSG_ERROR([failed!])
        fi
        AC_MSG_RESULT($(basename ${USE_TCLSH_LOCAL}))
    ],
    [])

# In order from most to least preferred
TCL_VERSION_PREFS="8.6 8.5"

USE_TCL=0
# Set USE_TCL
AC_ARG_WITH(tcl,
    AS_HELP_STRING([--with-tcl], [location of Tcl]),
    [
        USE_TCL=${withval}
    ],
    [
      AC_MSG_NOTICE([Tcl location not specified, trying to find tclsh on path])
      for TCLVER in ${TCL_VERSION_PREFS}
      do
        AC_MSG_CHECKING([for tclsh${TCLVER} on path])
        program=$( which tclsh${TCLVER} )
        if [[ ${?} == 0 ]]
        then
          AC_MSG_RESULT([yes: ${program}])
          USE_TCL=$( cd $(dirname ${program})/.. ; /bin/pwd )
          AC_MSG_RESULT([Found Tcl at ${USE_TCL}]);
          #AC_MSG_RESULT($(basename ${USE_TCLSH_LOCAL}))
          break
        else
          AC_MSG_RESULT([no])
        fi
      done

      if [[ ${USE_TCL} == 0 ]]
      then
        AC_MSG_ERROR([Could not find tclsh for any version: ${TCL_VERSION_PREFS}!])
      fi
    ]
)

# Sniff out default Tcl version and library path
TCL_VERSION=0

AC_ARG_WITH(tcl-version,
    AS_HELP_STRING([--with-tcl-version],
                   [Tcl version. Minimum is 8.5. If not specified,
                    use highest version we can find]),
    [
        TCL_VERSION=${withval}
        AC_MSG_NOTICE([specified Tcl version ${TCL_VERSION}])
        LIBTCL=libtcl${TCL_VERSION}.${SO_SUFFIX}
    ]
)

USE_TCL_LIB_DIR=0


AC_ARG_WITH(tcl-lib-dir,
    AC_HELP_STRING([--with-tcl-lib-dir],
                   [directory containing Tcl library]),
    [
        USE_TCL_LIB_DIR=${withval}
        AC_MSG_RESULT([using Tcl lib dir: ${USE_TCL_LIB_DIR}])
    ])

if [[ ${TCL_VERSION} != 0 ]]
then
  # We have locked in a version choice
  TCL_VERSION_PREFS=${TCL_VERSION}
fi


# Check in order from most to least preferred
for TCLVER in ${TCL_VERSION_PREFS}
do
  
  if [[ ${USE_TCL_LIB_DIR} == 0 ]]
  then
    LIB_DIR_OPTS="${USE_TCL}/lib ${USE_TCL}/lib64"
  else
    LIB_DIR_OPTS="${USE_TCL_LIB_DIR}"
  fi
  
  for LIB_DIR in ${LIB_DIR_OPTS}
  do
    LIBTCL=libtcl${TCLVER}.${SO_SUFFIX}
    AC_MSG_CHECKING([for Tcl library at ${LIB_DIR}/${LIBTCL}])
    if [[ -r ${LIB_DIR}/${LIBTCL} ]]
    then
      AC_MSG_RESULT([exists!])
      TCL_VERSION=${TCLVER}
      USE_TCL_LIB_DIR=${LIB_DIR}
      break 2
    else
      AC_MSG_RESULT([no])
    fi
  done
done

if [[ ! -r ${USE_TCL_LIB_DIR}/${LIBTCL} ]]
then
    AC_MSG_ERROR([could not find libtcl!])
fi

echo "using Tcl version: ${TCL_VERSION}"
AC_SUBST(TCL_VERSION)


# Set USE_TCL_CFG_DIR: location of tclConfig.sh
USE_TCL_CFG_DIR=0
AC_MSG_CHECKING([for tclConfig.sh ])
# This location is for Tcl installation from source
if [[ -r ${USE_TCL}/lib/tclConfig.sh ]]
then
    USE_TCL_CFG_DIR=${USE_TCL}/lib
# This is a location used by the APT tcl8.5-dev package (1)
elif [[ -r ${USE_TCL}/lib64/tclConfig.sh ]]
then
    USE_TCL_CFG_DIR=${USE_TCL}/lib64
# This is a location used by the APT tcl8.5-dev package (2)
elif [[ -r ${USE_TCL}/lib/tcl${TCL_VERSION}/tclConfig.sh ]]
then
    USE_TCL_CFG_DIR=${USE_TCL}/lib/tcl${TCL_VERSION}
fi
if [[ ${USE_TCL_CFG_DIR} == 0 ]]
then
    AC_MSG_ERROR([Could not find tclConfig.sh!])
fi
AC_MSG_RESULT([ok])
AC_MSG_RESULT([using tclConfig.sh in: ${USE_TCL_CFG_DIR}/])
source ${USE_TCL_CFG_DIR}/tclConfig.sh
if [[ ${?} != 0 ]]
then
    AC_MSG_ERROR([could not source: ${USR_TCL_CFG_DIR}/tclConfig.sh])
fi

# TCL_VERSION needed by find-tcl.zsh
export TCL_VERSION

# Find tclsh binary name (may be tclsh8.5)
if [[ ${USE_TCLSH_LOCAL} == 0 ]]
then
    USE_TCLSH=$( maint/find-tcl.zsh ${USE_TCL} )
    if [[ ${?} != 0 ]]
    then
        AC_MSG_ERROR([Could not find Tcl ${TCL_VERSION} binary in ${USE_TCL}!])
    fi
    USE_TCLSH_LOCAL=${USE_TCLSH}
else
    export NO_RUN=1
    USE_TCLSH=$( maint/find-tcl.zsh ${USE_TCL} )
    if [[ ${?} != 0 ]]
    then
        AC_MSG_ERROR([Could not find Tcl ${TCL_VERSION} binary at ${USE_TCL}!])
    fi
fi
AC_MSG_RESULT([using Turbine Tcl executable: ${USE_TCLSH}])

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

AC_SUBST(USE_TCL)
AC_SUBST(USE_TCL_LIB_DIR)
AC_SUBST(USE_TCLSH)
AC_SUBST(USE_TCLSH_LOCAL)
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

ENABLE_FAST=0
AC_ARG_ENABLE(fast,
    AS_HELP_STRING([--enable-fast],
                   [Enable NDEBUG, -O3, disable log.]),
    ENABLE_FAST=1
    DISABLE_LOG=1
    CFLAGS="${CFLAGS} -O3 -DNDEBUG")
AC_SUBST(ENABLE_FAST)
AC_SUBST(CFLAGS)

USE_XLC=0
AC_ARG_ENABLE(xlc,
              AS_HELP_STRING(
                    [--enable-xlc],
                    [Support IBM XLC on BG/P]),
              [
                USE_XLC=1
              ])
AC_SUBST(USE_XLC)

AC_DEFINE_UNQUOTED(DISABLE_LOG, $DISABLE_LOG, [Disables logging])

ENABLE_BGP=0
AC_ARG_ENABLE(bgp,
    AS_HELP_STRING([--enable-bgp],
                   [Special handling for BG/P.]),
    ENABLE_BGP=1)
AC_DEFINE_UNQUOTED(ENABLE_BGP, $ENABLE_BGP,
                   [Special handling for BG/P.])

ENABLE_DEV=0
AC_ARG_ENABLE(dev,
    AS_HELP_STRING([--enable-dev],
                   [Turn on more warnings for developers.
                    Default: disabled]),
    ENABLE_DEV=1)
AC_SUBST(ENABLE_DEV)

ENABLE_SHARED=1
AC_ARG_ENABLE(shared,
    AS_HELP_STRING([--enable-shared],
                   [Builds shared libraries.
                    Required to build dynamically loadable Tcl package.
                    Default: enabled]),
    [
       if test "$enableval" = no ; then
         ENABLE_SHARED=0
       fi
    ],
    [
    ])
AC_SUBST(ENABLE_SHARED)

ENABLE_STATIC=0
AC_ARG_ENABLE(static,
    AS_HELP_STRING([--enable-static],
                   [Default: disabled]),
    [
       if test "$enableval" = yes ; then
         ENABLE_STATIC=1
       fi
    ],
    [
    ])
AC_SUBST(ENABLE_STATIC)

if [[ "$ENABLE_STATIC" = 1 ]]; then
  # Need ar and ranlib to link
  AC_CHECK_PROG(AR, ar, ar, AC_MSG_ERROR(Cannot find ar))
  AC_PROG_RANLIB
fi

# We take system libraries out of arguments to the linker (#546)
ldconfig -v 2>/dev/null | grep -vP '\t' | cut -d : -f 1 > system-libs.txt

AC_CONFIG_FILES(
        src/turbine/turbine-version.h
        src/turbine/module.mk
        src/util/module.mk
        src/tcl/module.mk
        src/tcl/adlb/module.mk
        src/tcl/blob/module.mk
        src/tcl/c-utils/module.mk
        src/tcl/mpe/module.mk
        src/tcl/python/module.mk
        src/tcl/r/module.mk
        src/tcl/static-pkg/module.mk
        src/tcl/turbine/module.mk
        src/executables/module.mk
        lib/module.mk
        libstatic/module.mk
        scripts/submit/module.mk
        scripts/submit/cobalt/module.mk
        scripts/submit/cray/module.mk
        scripts/submit/ec2/module.mk
        scripts/submit/pbs/module.mk
        scripts/submit/slurm/module.mk
        scripts/turbine-config.sh
        tests/module.mk
        tests/runbin.zsh
	Makefile
)

AC_OUTPUT
