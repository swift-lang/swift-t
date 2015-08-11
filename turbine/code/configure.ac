
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

m4_include([../../dev/m4/ifblank.m4])

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

# Set shared object suffix (.so on Linux, .dylib on Mac)
SO_SUFFIX=so
if [[ $(uname) = "Darwin" ]]
then
   SO_SUFFIX=dylib
fi
AR_SUFFIX=a

DPKG_ARCH=
# Debian distros may put in architecture-specific subdirectory
if which dpkg-architecture > /dev/null 2>&1
then
  DPKG_ARCH=$(dpkg-architecture -qDEB_HOST_MULTIARCH)
  AC_MSG_RESULT([dpkg-architecture: ${DPKG_ARCH}])
fi

# Checks for programs
AC_PROG_CC
AC_PATH_PROG([SWIG],[swig],[0])
[[ ${SWIG} == 0 ]] && AC_MSG_ERROR([Could not find SWIG!])
# We trust the user's install and omit AC_PROG_INSTALL

# Note that the zlib check is too smart- if you provide a
# broken location for --with-zlib, it will find a different one!
# This is problematic on cross-compiled systems (like BG/Q).
ENABLE_ZLIB=0
m4_include([../../dev/m4/ax_check_zlib.m4])
AX_CHECK_ZLIB([ENABLE_ZLIB=1],
   AC_MSG_ERROR([Could not find zlib: use --with-zlib or --without-zlib.
                 Note that Swift/Turbine checkpointing and HDF
                 both require zlib and can both be disabled.]))
AC_SUBST(ZLIB_HOME)
AC_SUBST(ENABLE_ZLIB)

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
AC_TYPE_UINT64_T
AC_CHECK_TYPES([ptrdiff_t])

# Checks for library functions.
# Although autoscan 2.68 warns for AC_FUNC_STRNLEN, we do not use it
# because of known autoconf issues.  We do check for strnlen here:
AC_FUNC_FORK
AC_FUNC_MALLOC
AC_FUNC_REALLOC
AC_CHECK_FUNCS([gettimeofday mallinfo memchr memset pow setenv strchr])
AC_CHECK_FUNCS([strdup strerror strlen strnlen strstr strtol strtoul ])
AC_CHECK_FUNCS([uname])

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

USE_LIBCUTILS_A=""
echo "checking for ${USE_C_UTILS}/lib/libexmcutils.${AR_SUFFIX}"
if [[ -f "${USE_C_UTILS}/lib/libexmcutils.${AR_SUFFIX}" ]]; then
  USE_LIBCUTILS_A="${USE_C_UTILS}/lib/libexmcutils.${AR_SUFFIX}"
fi
AC_SUBST(USE_LIBCUTILS_A)

# MPI defaults (may be overridden on special systems)
USE_MACHINE=normal
# The "launcher" is used by job submission systems to launch jobs
# Usually a path to mpiexec
# Note: schedulers are handled by turbine/scripts/submit/*
USE_LAUNCHER=0
USE_MPI_DIR=0
USE_MPI_INCLUDE=0
USE_MPI_LIB_DIR=0
USE_MPI_LIB_NAME=
USE_MPI_LIB_DIR=
ENABLE_CUSTOM_MPI=0

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
        AC_MSG_RESULT([Trying MPI directory: ${USE_MPI_DIR}])
    ],
    [])

# In custom-mpi, the user will optionally specify MPI directories below
AC_ARG_ENABLE(custom-mpi,
    AS_HELP_STRING([--enable-custom-mpi],
                   [Select MPI include and lib separately,
                    do not use mpicc]),
    [
        ENABLE_CUSTOM_MPI=1
        USE_MACHINE=custom
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
   program=$( which mpicc 2> /dev/null)
   if [[ ${?} == 0 ]]
   then
       AC_MSG_RESULT([PATH contains: ${program}])
       USE_MPI_DIR=$( cd $(dirname ${program})/.. ; /bin/pwd )
       USE_MPI_INCLUDE=${USE_MPI_DIR}/include
       AC_MSG_RESULT([Trying MPI directory: ${USE_MPI_DIR}])
   elif (( ! ENABLE_CUSTOM_MPI ))
   then
       AC_MSG_ERROR([Turbine requires --with-mpi or
                                  --enable-custom-mpi or
                                  or mpicc in PATH .])
   fi
fi

if [[ ${USE_LAUNCHER} == 0 ]]
then
    # Try to set launcher based on MPI_DIR
    if [[ -x ${USE_MPI_DIR}/bin/mpiexec ]]
    then
        AC_MSG_RESULT([Found ${USE_MPI_DIR}/bin/mpiexec])
        USE_LAUNCHER=${USE_MPI_DIR}/bin/mpiexec
    fi
fi

if [[ ${USE_LAUNCHER} == 0 ]]
then
    # Try to set launcher based on PATH
    program=$( which mpiexec 2> /dev/null )
    if [[ ${?} != 0 ]]
    then
        AC_MSG_WARN([mpiexec not found: no default Turbine launcher])
    fi
fi

AC_MSG_RESULT([Using MPI launcher: ${USE_LAUNCHER}])

# Look for mpi.h at its APT location
if [[ ! -f ${USE_MPI_INCLUDE}/mpi.h ]]
then
    if [[ -f ${USE_MPI_INCLUDE}/mpi/mpi.h ]]
    then
        USE_MPI_INCLUDE=${USE_MPI_INCLUDE}/mpi
    fi
fi

AC_ARG_WITH(mpi-include,
    AS_HELP_STRING([--with-mpi-include],
                   [directory containing MPI header (mpi.h).]),
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

MPI_EXTRA_LIBS=
AC_ARG_WITH(mpi-lib-extra,
    AS_HELP_STRING([--with-mpi-lib-extra],
                   [comma-separated list of names of any additional MPI
                    libraries in MPI lib directory that must be linked
                    against.  Mainly useful for static linking against
                    uncommon MPI implementations or when not using
                    mpicc.]),
    [
      # Switch to space separated
      MPI_EXTRA_LIBS=$(echo "${withval}" | tr "," " ")
    ],
    [])

# Allow user to disable MPI checks (useful on Cray)
ENABLE_MPI_CHECKS=yes
AC_ARG_ENABLE(mpi-checks,
    AC_HELP_STRING([--disable-mpi-checks],
                   [Turbine checks for mpi.h and MPI libs to obtain
                    the MPI version and enforce sanity.  This flag
                    disables these checks.]),
    [ ENABLE_MPI_CHECKS=${enableval} ])

# Now check MPI settings

if [[ ${ENABLE_MPI_CHECKS} == yes ]]
then

AC_CHECK_FILE(${USE_MPI_INCLUDE}/mpi.h,
                [],
                [AC_MSG_ERROR([Could not find mpi.h in ${USE_MPI_INCLUDE}])])

MPI_LIB_FOUND=0

if [[ -z "${USE_MPI_LIB_DIR}" ]]
then
  MPI_LIB_DIR_ALTS="${USE_MPI_DIR}/lib"

  if [[ ! -z "${DPKG_ARCH}" ]]
  then
    MPI_LIB_DIR_ALTS+=" ${USE_MPI_DIR}/lib/${DPKG_ARCH}"
  fi
else
  MPI_LIB_DIR_ALTS="${USE_MPI_LIB_DIR}"
fi

if [[ -z "${USE_MPI_LIB_NAME}" ]]
then
  MPI_LIB_NAME_ALTS="mpi mpich"
else
  MPI_LIB_NAME_ALTS="${USE_MPI_LIB_NAME}"
fi

for MPI_LIB_DIR_ALT in ${MPI_LIB_DIR_ALTS}
do
  for MPI_LIB_NAME_ALT in ${MPI_LIB_NAME_ALTS}
  do
    MPI_LIB_SO=${MPI_LIB_DIR_ALT}/lib${MPI_LIB_NAME_ALT}.${SO_SUFFIX}
    MPI_LIB_A=${MPI_LIB_DIR_ALT}/lib${MPI_LIB_NAME_ALT}.${AR_SUFFIX}
    AC_MSG_CHECKING([for MPI library lib${MPI_LIB_NAME_ALT} at ${MPI_LIB_DIR_ALT}])
    if [[ -f "${MPI_LIB_SO}" -o -f "${MPI_LIB_A}"  ]]
    then
      AC_MSG_RESULT([yes])
      MPI_LIB_FOUND=1
      USE_MPI_LIB_DIR="${MPI_LIB_DIR_ALT}"
      USE_MPI_LIB_NAME="${MPI_LIB_NAME_ALT}"
      break 2
    else
      AC_MSG_RESULT([no])
    fi
  done
done

if (( ! MPI_LIB_FOUND ))
then
  AC_MSG_ERROR([Could not find MPI library in any alternative locations])
fi
fi

# Set MPI options

if [[ -n "${USE_MPI_LIB_NAME}" ]]
then
    USE_MPI_LIB_SPEC="-l${USE_MPI_LIB_NAME}"
fi
for extra_lib in ${MPI_EXTRA_LIBS}
do
  USE_MPI_LIB_SPEC+=" -l${extra_lib}"
done

USE_MPI_LIB_A=
USE_MPI_LIB_EXTRA_A=
if [[ -f ${MPI_LIB_A} ]]
then
  MPI_LIB_FOUND=1
  USE_MPI_LIB_A=${MPI_LIB_A}

  for extra_lib in ${MPI_EXTRA_LIBS}
  do
    extra_lib_a=${USE_MPI_LIB_DIR}/lib${extra_lib}.${AR_SUFFIX}
    if [[ ! -f "${extra_lib_a}" ]]
    then
      AC_MSG_ERROR([Expected library ${extra_lib_a} to exist])
    fi
    USE_MPI_LIB_EXTRA_A+=" ${extra_lib_a}"
  done

  # Locate common extra dependencies
  for extra_lib in opa mpl open-rte open-pal
  do
    extra_lib_a=${USE_MPI_LIB_DIR}/lib${extra_lib}.${AR_SUFFIX}
    if [[ -f "${extra_lib_a}" ]]
    then
      USE_MPI_LIB_EXTRA_A+=" ${extra_lib_a}"
      USE_MPI_LIB_SPEC+=" -l${extra_lib}"
    fi
  done
fi

if [[ ${ENABLE_MPI_CHECKS} == yes ]]
then
if (( ! MPI_LIB_FOUND ))
then
  AC_MSG_ERROR([Could not find MPI library:
                   ${MPI_LIB_SO} or ${MPI_LIB_A}.
                   MPI may not be installed at this
                   location or library may not
                   have been built.])
fi
fi

# Write out MPI settings
AC_SUBST(USE_MACHINE)
AC_SUBST(USE_LAUNCHER)
AC_SUBST(USE_MPI_INCLUDE)
AC_SUBST(USE_MPI_LIB_DIR)
AC_SUBST(USE_MPI_LIB_NAME)
AC_SUBST(USE_MPI_LIB_SPEC)
AC_SUBST(USE_MPI_LIB_A)
AC_SUBST(USE_MPI_LIB_EXTRA_A)

# Discover MPI implementation:
# MPI_IMPL will be unknown, OpenMPI, or MPICH
MPI_IMPL=unknown
# Set to flag for line numbers if supported
MPI_LINE_OUTPUT_FLAG=

if [[ ${ENABLE_MPI_CHECKS} == yes ]]
then
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
   if (( OMPI_MINOR_VERSION >= 3 ))
   then
     LINE_PREFIX="-tag-output"
   else
     LINE_PREFIX=
   fi
elif grep -q MPICH_NAME ${MPI_H} > /dev/null 2>&1
then
   MPI_IMPL=MPICH2
   MPI_LINE_OUTPUT_FLAG=-l
fi
AC_MSG_RESULT([${MPI_IMPL}])
fi # ENABLE_MPI_CHECKS==yes
AC_SUBST(MPI_IMPL)
AC_SUBST(MPI_LINE_OUTPUT_FLAG)

# We assume libmpe.so is in the MPE/lib directory
# See Turbine - Build for explanation
ENABLE_MPE=0
USE_MPE=0
USE_MPE_LIB_A=
LIBMPE_SO=${USE_MPE}/lib/libmpe.${SO_SUFFIX}
LIBMPE_A=${USE_MPE}/lib/libmpe.${AR_SUFFIX}
AC_ARG_WITH(mpe,
     AS_HELP_STRING(
                    [--with-mpe],
                    [Enable Turbine MPE, set path to ${LIBMPE}]),
     [ USE_MPE=${withval} ; ENABLE_MPE=1], [:] )
if [[ ${USE_MPE} != 0 ]]
then
    AC_MSG_RESULT([Enabling MPE]);

    LIBMPE_FOUND=0
    if [[ -f ${LIBMPE_SO} ]]
    then
      LIBMPE_FOUND=1
    fi

    if [[ -f ${LIBMPE_A} ]]
    then
      LIBMPE_FOUND=1
      USE_MPI_LIB_A=${LIBMPE_A}
    fi

    if (( ! LIBMPE_FOUND ))
    then
      AC_MSG_ERROR([Could not find MPE library:
                       ${LIBMPE_SO} or ${LIBMPE_A}.
                       MPE may not be installed at this
                       location or library may not
                       have been built.])
    fi
    AC_DEFINE_UNQUOTED(ENABLE_MPE,ENABLE_MPE,[Enables MPE])
fi
AC_SUBST(USE_MPE)
AC_SUBST(ENABLE_MPE)
AC_SUBST(USE_MPE_LIB_A)

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

USE_LIBADLB_A=""
if [[ -f "${USE_ADLB}/lib/libadlb.${AR_SUFFIX}" ]]; then
  USE_LIBADLB_A="${USE_ADLB}/lib/libadlb.${AR_SUFFIX}"
fi
AC_SUBST(USE_LIBADLB_A)

# Python is optional
# By default, use default location
USE_PYTHON_INCLUDE=0
USE_PYTHON_LIB=0
USE_PYTHON_NAME=python2.7
HAVE_PYTHON=0

AC_ARG_ENABLE(python,
    AS_HELP_STRING([--enable-python],
                   [Enable calling python]),
    [
      HAVE_PYTHON=1
    ])

AC_ARG_WITH(python,
   AS_HELP_STRING([--with-python],
                   [Use this python directory]),
  [
      HAVE_PYTHON=1
      AC_CHECK_FILE(${withval}/include/python2.7/Python.h, [],
                    [AC_MSG_ERROR([Could not find python header!])])
      USE_PYTHON_INCLUDE=${withval}/include
      USE_PYTHON_LIBDIR=${withval}/lib
      USE_PYTHON_NAME=libpython2.7.so
  ])

AC_ARG_WITH(python-lib,
    AS_HELP_STRING([--with-python-lib],
                   [Use this python directory library -
                    provide fully qualified path]),
    [
      HAVE_PYTHON=1
      USE_PYTHON_LIB=${withval}
      AC_CHECK_FILE(${USE_PYTHON_LIB}, [],
                    [AC_MSG_ERROR([Could not find python!])])
      USE_PYTHON_LIBDIR=$( dirname ${USE_PYTHON_LIB} )
      USE_PYTHON_NAME=$( basename ${USE_PYTHON} )
      USE_PYTHON=${USE_PYTHON_LIB} # Copy
    ])

if [[ ${HAVE_PYTHON} == 1 ]]
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

HAVE_JULIA=0
USE_JULIA=0
AC_ARG_WITH(julia,
   AS_HELP_STRING([--with-julia],
                   [Use this julia source directory]),
  [
      HAVE_JULIA=1
      USE_JULIA=${withval}
      AC_CHECK_FILE(${USE_JULIA}/src/julia.h, [],
                    [AC_MSG_ERROR([Could not find julia header!])])
  ])
AC_DEFINE_UNQUOTED(HAVE_JULIA,${HAVE_JULIA},[Enables Julia])
AC_SUBST(HAVE_JULIA)
AC_SUBST(USE_JULIA)

if (( HAVE_JULIA ))
then
    echo "Julia enabled"
else
    echo "Julia disabled"
fi

HAVE_COASTER=0
USE_COASTER=0
AC_ARG_WITH(coaster,
   AS_HELP_STRING([--with-coaster],
                   [Use this coaster c client install directory]),
  [
      HAVE_COASTER=1
      USE_COASTER=${withval}
      AC_CHECK_FILE(${USE_COASTER}/include/coaster.h, [],
                    [AC_MSG_ERROR([Could not find coaster C client header!])])
  ])
AC_DEFINE_UNQUOTED(HAVE_COASTER,${HAVE_COASTER},[Enables Coaster Interface])
AC_SUBST(HAVE_COASTER)
AC_SUBST(USE_COASTER)

if (( HAVE_COASTER ))
then
    AC_MSG_RESULT([Coaster integration enabled])
else
    AC_MSG_RESULT([Coaster integration disabled])
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
    ])
AC_SUBST(ENABLE_SHARED)

ENABLE_STATIC_PKG=1
AC_ARG_ENABLE(static-pkg,
    AS_HELP_STRING([--enable-static-pkg],
       [Create statically linkable Turbine Tcl package with Tcl source.
        Default: yes]),
    [
       if test "$enableval" = no ; then
         ENABLE_STATIC_PKG=0
       elif test "$enable_val" = yes ; then
         ENABLE_STATIC_PKG=1
       fi
    ])
AC_SUBST(ENABLE_STATIC_PKG)

ENABLE_STATIC=1
AC_ARG_ENABLE(static,
    AS_HELP_STRING([--enable-static],
             [Create static libraries.
              Default: enabled]),
  [
       if test "$enableval" = no ; then
         ENABLE_STATIC=0
       fi
    ])
AC_SUBST(ENABLE_STATIC)

# Need ar and ranlib to build static libraries
AC_CHECK_PROG(AR, ar, ar, AC_MSG_ERROR(Cannot find ar))
AC_PROG_RANLIB

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
                   [directory containing Tcl shared library]),
    [
        USE_TCL_LIB_DIR=${withval}
        AC_MSG_RESULT([using Tcl lib dir: ${USE_TCL_LIB_DIR}])
    ])

if [[ ${TCL_VERSION} != 0 ]]
then
  # We have locked in a version choice
  TCL_VERSION_PREFS=${TCL_VERSION}
fi

# Can always use static Tcl library
TCL_LIB_SUFFIX_PREFS="a"
if [[ ${ENABLE_SHARED} != 0 ]]
then
  # Can use shared library in this case
  TCL_LIB_SUFFIX_PREFS="${SO_SUFFIX} ${TCL_LIB_SUFFIX_PREFS}"
fi


# Locate one or both variant of libtcl
USE_LIBTCL_A=
USE_LIBTCL_SO=

# Check in order from most to least preferred
for TCLVER in ${TCL_VERSION_PREFS}
do

  if [[ ${USE_TCL_LIB_DIR} == 0 ]]
  then
    LIB_DIR_OPTS="${USE_TCL}/lib ${USE_TCL}/lib64"

    # Debian distros may put in architecture-specific subdirectory
    if [[ ! -z "${DPKG_ARCH}" ]]
    then
      LIB_DIR_ARCH_OPTS=""
      for opt in $LIB_DIR_OPTS
      do
        LIB_DIR_ARCH_OPTS+=" $opt/${DPKG_ARCH}"
      done
      LIB_DIR_OPTS="$LIB_DIR_OPTS $LIB_DIR_ARCH_OPTS"
    fi
  else
    LIB_DIR_OPTS="${USE_TCL_LIB_DIR}"
  fi

  for TCL_LIB_SUFFIX in ${TCL_LIB_SUFFIX_PREFS}
  do
    for LIB_DIR in ${LIB_DIR_OPTS}
    do
      LIBTCL_PREFIX=libtcl${TCLVER}
      LIBTCL=${LIBTCL_PREFIX}.${TCL_LIB_SUFFIX}
      AC_MSG_CHECKING([for Tcl library at ${LIB_DIR}/${LIBTCL}])
      if [[ -r ${LIB_DIR}/${LIBTCL} ]]
      then
        AC_MSG_RESULT([exists!])
        TCL_VERSION=${TCLVER}
        USE_TCL_LIB_DIR=${LIB_DIR}

        # We found the library - work out which variants are present
        MAYBE_LIBTCL_SO="${LIB_DIR}/${LIBTCL_PREFIX}.${SO_SUFFIX}"
        MAYBE_LIBTCL_A="${LIB_DIR}/${LIBTCL_PREFIX}.${AR_SUFFIX}"
        if [[ -r "${MAYBE_LIBTCL_SO}" ]]
        then
          USE_LIBTCL_SO="${MAYBE_LIBTCL_SO}"
        fi
        if [[ -r "${MAYBE_LIBTCL_A}" ]]
        then
          USE_LIBTCL_A="${MAYBE_LIBTCL_A}"
        fi
        break 3
      else
        AC_MSG_RESULT([no])
      fi
    done
  done
done

if [[ -z "${USE_LIBTCL_A}" -a -z "${USE_LIBTCL_SO}" ]]
then
    AC_MSG_ERROR([could not find libtcl!])
fi

echo "using Tcl version: ${TCL_VERSION}"
echo "Tcl shared library: ${USE_LIBTCL_SO}"
echo "Tcl static library: ${USE_LIBTCL_A}"
AC_SUBST(TCL_VERSION)

# Set USE_TCL_CFG_DIR: location of tclConfig.sh
USE_TCL_CFG_DIR=0

# This is a location used e.g. by the APT tcl8.6-dev package.
# Put first to maximize chance of getting Tcl version correct
TCL_CFG_DIR_ALTS="${USE_TCL_LIB_DIR}/tcl${TCL_VERSION}"
# This location is for Tcl installation from source
TCL_CFG_DIR_ALTS+=" ${USE_TCL_LIB_DIR}"

for TCL_CFG_DIR_ALT in $TCL_CFG_DIR_ALTS
do
  AC_MSG_CHECKING([for tclConfig.sh at ${TCL_CFG_DIR_ALT}])
  if [[ -r "$TCL_CFG_DIR_ALT/tclConfig.sh" ]]
  then
    USE_TCL_CFG_DIR="${TCL_CFG_DIR_ALT}"
    AC_MSG_RESULT([found])
    break
  else
    AC_MSG_RESULT([no])
  fi
done

if [[ ${USE_TCL_CFG_DIR} == 0 ]]
then
    AC_MSG_ERROR([Could not find tclConfig.sh!])
fi
AC_MSG_RESULT([using tclConfig.sh in: ${USE_TCL_CFG_DIR}/])

# Make copy of old version since it can be clobbered by tclConfig.sh
TCL_OLD_VERSION="$TCL_VERSION"

# Source tclConfig.sh to obtain Tcl settings
source ${USE_TCL_CFG_DIR}/tclConfig.sh
if [[ ${?} != 0 ]]
then
    AC_MSG_ERROR([could not source: ${USR_TCL_CFG_DIR}/tclConfig.sh])
fi
if [[ "$TCL_VERSION" != "$TCL_OLD_VERSION" ]]
then
  AC_MSG_ERROR([${USE_TCL_CFG_DIR}/tclConfig.sh is not for appropriate \
      Tcl version.  Expected ${TCL_OLD_VERSION} but got ${TCL_VERSION}])
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

AC_MSG_CHECKING([for Tcl system library directory with init.tcl])
USE_TCL_SYSLIB_DIR=
AC_ARG_WITH(tcl-syslib-dir,
      AS_HELP_STRING(
        [[--with-tcl-syslib-dir],
         [tcl system library directory.  Must contain init.tcl in \
          root or tcl${TCL_VERSION} subdirectory]]),
        [USE_TCL_SYSLIB_DIR="${withval}"])

mktemp_system()
{
    if [[ ${USE_MAC} == "no" ]]
    then
        mktemp
    else
        mktemp -t TURBINE
    fi
}

if [[ -z "${USE_TCL_SYSLIB_DIR}" ]]
then
  # Put script in file as way to get tclsh to crash on invalid script
  tmp_script=$(mktemp_system)

  # Try to find out the library directory from tclsh
  echo 'puts [[ file dir [ info library ] ]]' > ${tmp_script}
  USE_TCL_SYSLIB_DIR=$( ${USE_TCLSH} $tmp_script )
  TCLSH_EXIT=$?
  rm $tmp_script
  if [[ $? != 0 ]]; then
    AC_MSG_ERROR([tcl-syslib-dir not provided and could not determine \
                  system library location by invoking ${USE_TCLSH}])
  fi
fi

if [[ ! -d "${USE_TCL_SYSLIB_DIR}" ]]
then
  AC_MSG_ERROR([Error finding tcl-syslib-dir: ${USE_TCL_SYSLIB_DIR} \
               not a directory])
fi

INIT_TCL_FOUND=0
# Try to locate init.tcl.  In some layouts it's in the root, in others
# it's in the tcl${TCL_VERSION} subdirectory
for syslib_dir in "${USE_TCL_SYSLIB_DIR}" \
                  "${USE_TCL_SYSLIB_DIR}/tcl${TCL_VERSION}"
do
  MAYBE_INIT_TCL="${syslib_dir}/init.tcl"
  if [[ -f ${MAYBE_INIT_TCL} ]]
  then
    INIT_TCL_FOUND=1
    AC_MSG_RESULT([yes])
    AC_MSG_RESULT([Found init.tcl: ${MAYBE_INIT_TCL}])
  fi
done

if (( ! INIT_TCL_FOUND ))
then
  AC_MSG_ERROR([could not locate init.tcl under ${USER_TCL_SYSLIB_DIR}:\
          appears not to be a legimitate Tcl system library directory])
fi

AC_MSG_RESULT([using Tcl system library directory: ${USE_TCL_SYSLIB_DIR}])

AC_SUBST(USE_TCL)
AC_SUBST(USE_TCL_LIB_DIR)
AC_SUBST(USE_TCL_SYSLIB_DIR)
AC_SUBST(USE_LIBTCL_A)
AC_SUBST(USE_LIBTCL_SO)
AC_SUBST(USE_TCLSH)
AC_SUBST(USE_TCLSH_LOCAL)
AC_SUBST(TCL_INCLUDE_SPEC)
AC_SUBST(TCL_LIB_SPEC)
# Tcl library dependencies for static build
TCL_LIB_SPEC_EXTRA="${TCL_LIBS}"
AC_SUBST(TCL_LIB_SPEC_EXTRA)

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

USE_XLC=0
AC_ARG_ENABLE(xlc,
              AS_HELP_STRING(
                    [--enable-xlc],
                    [Enable IBM XLC flags]),
              [
                USE_XLC=1
              ])
AC_SUBST(USE_XLC)

m4_include([../../dev/m4/ax_lib_hdf5.m4])
AX_LIB_HDF5()
if [[ "${HAVE_HDF5}" == 1 ]]
then
    echo "HDF5 enabled"
else
    echo "HDF5 disabled"
    echo "Note: HDF5 is only required if you need Turbine's HDF features."
fi

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

AC_SUBST(CFLAGS)
AC_SUBST(LDFLAGS)

m4_include([../../dev/m4/syslibs.m4])

echo
echo "Preparing config files: please wait..."

AC_CONFIG_FILES(
        src/turbine/turbine-version.h
        src/turbine/module.mk
        src/util/module.mk
        src/util/debug-tokens.tcl
        src/tcl/module.mk
        src/tcl/adlb/module.mk
        src/tcl/blob/module.mk
        src/tcl/c-utils/module.mk
        src/tcl/mpe/module.mk
        src/tcl/julia/module.mk
        src/tcl/python/module.mk
        src/tcl/r/module.mk
        src/tcl/static-pkg/module.mk
        src/tcl/turbine/module.mk
        src/executables/module.mk
        lib/module.mk
        scripts/main-wrap/module.mk
        scripts/submit/module.mk
        scripts/submit/cobalt/module.mk
        scripts/submit/cray/module.mk
        scripts/submit/ec2/module.mk
        scripts/submit/pbs/module.mk
        scripts/submit/slurm/module.mk
        scripts/turbine-config.sh
        scripts/turbine-build-config.sh
        tests/module.mk
        tests/runbin.zsh
	Makefile
)

AC_OUTPUT
