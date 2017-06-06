
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

AC_MSG_RESULT([PREFIX: ${prefix}])
SOFTWARE=$( cd $(dirname ${prefix}) && /bin/pwd )
if [[ ${?} != 0 ]]
then
  SOFTWARE=0
fi
AC_MSG_RESULT([SOFTWARE: ${SOFTWARE}])

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

DEBIAN_BUILD=0
AC_ARG_ENABLE(debian-build,
  AS_HELP_STRING(
    [--enable-debian-build], [configure for a Debian package]),
    [DEBIAN_BUILD=1]
)
AC_SUBST(DEBIAN_BUILD)

# Check for CC
AC_PROG_CC

# Report location of CC
USE_CC=$( which $CC )
AC_MSG_RESULT([using CC=$USE_CC])
AC_SUBST(USE_CC)

# Checks for programs
AC_PROG_LN_S
LD=${CC}
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
AC_CHECK_FUNCS([dup2 gettimeofday mallinfo memchr memset pow setenv])
AC_CHECK_FUNCS([strchr strdup strerror strlen strnlen strstr])
AC_CHECK_FUNCS([strtol strtoul uname])

# Define templates
AC_DEFINE([HAVE_STDDEF_H], [], [Do we have stddef.h?])
AC_DEFINE([HAVE_SYS_PARAM_H], [], [Do we have sys/param.h?])

USE_C_UTILS=0
AC_ARG_WITH(c-utils,
    AS_HELP_STRING([--with-c-utils], [location of ExM c-utils]),
    [
        AC_MSG_CHECKING([for ${withval}/include/c-utils.h])
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

        if (( ${DEBIAN_BUILD} ))
        then
            USE_C_UTILS=/usr
        else # We are doing a source installation
             location=${SOFTWARE}/c-utils
             AC_CHECK_FILE(${location}/include/c-utils.h,
                           [USE_C_UTILS=${location}], [])
        fi
        [[ ${USE_C_UTILS} == 0 ]] && AC_MSG_ERROR([Not found: ExM c-utils])
    ]
)
AC_SUBST(USE_C_UTILS)

USE_LIBCUTILS_A=""
AC_MSG_CHECKING([for ${USE_C_UTILS}/lib/libexmcutils.${AR_SUFFIX}])
if [[ -f "${USE_C_UTILS}/lib/libexmcutils.${AR_SUFFIX}" ]]; then
  USE_LIBCUTILS_A="${USE_C_UTILS}/lib/libexmcutils.${AR_SUFFIX}"
  AC_MSG_RESULT([yes])
else
  AC_MSG_RESULT([no])
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

ENABLE_XPT=no
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
        AC_MSG_ERROR([Invalid enable-checkpoint value: $enableval])
      fi
    ], [])
if [[ ${ENABLE_XPT} == yes ]]
then
    AC_MSG_RESULT([Checkpointing enabled])
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
        [[ ${SOFTWARE} == 0 ]] && AC_MSG_ERROR([Not found: ADLB])

        if (( ${DEBIAN_BUILD} ))
        then
            USE_ADLB=/usr
        else # We are doing a source installation
            location=${SOFTWARE}/lb
            AC_CHECK_FILE(${location}/include/adlb.h,
                          [USE_ADLB=${location}], [])
            [[ ${USE_ADLB} == 0 ]] && AC_MSG_ERROR([Not found: ADLB])
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
AC_ARG_ENABLE(python,
   AS_HELP_STRING([--enable-python],
                  [Enable calling Python language (searches PATH for python executable)]),
  [
      HAVE_PYTHON=1
      PYTHON_EXE=""
  ])

AC_ARG_WITH(python-exe,
   AS_HELP_STRING([--with-python-exe],
                  [Specify complete path to python executable]),
  [
      if test "x${withval}" == "xyes"; then
          # Look for exe in PATH below
          PYTHON_EXE=""
      else
          PYTHON_EXE=${withval}
          if test "x$PYTHON_EXE" != "x"; then
              AC_CHECK_FILE(${PYTHON_EXE}, [],
                [AC_MSG_ERROR([Specified python exe not found!  Tried: $PYTHON_EXE])])
          fi
      fi
      HAVE_PYTHON=1
  ])

if (( HAVE_PYTHON ))
then
    if test "x$PYTHON_EXE" == "x"; then
        AC_PATH_PROG([PYTHON_EXE], [python], [NOT_FOUND])
    fi
    if test "x$PYTHON_EXE" == "xNOT_FOUND"; then
        AC_MSG_ERROR([Unable to find given python executable in PATH])
    fi
    TURBINE_CODEDIR=`pwd`
    PYTHON_INCLUDE_FLAGS=$(${PYTHON_EXE} ${TURBINE_CODEDIR}/scripts/python-config.py --include-flags) || AC_MSG_ERROR([Failed to execute python-config.py --include-flags])
    PYTHON_LIB_FLAGS=$(${PYTHON_EXE} ${TURBINE_CODEDIR}/scripts/python-config.py --lib-flags) || AC_MSG_ERROR([Failed to execute python-config.py --lib-flags])
    PYTHON_LIBDIR=$(${PYTHON_EXE} ${TURBINE_CODEDIR}/scripts/python-config.py --lib-dir) || AC_MSG_ERROR([Failed to execute python-config --lib-dir])
    PYTHON_NAME=$(${PYTHON_EXE} ${TURBINE_CODEDIR}/scripts/python-config.py --lib-name) || AC_MSG_ERROR([Failed to execute python-config --lib-name])
    AC_MSG_RESULT([Python enabled])
    AC_MSG_RESULT([Using Python include flags: ${PYTHON_INCLUDE_FLAGS}])
    AC_MSG_RESULT([Using Python lib flags:     ${PYTHON_LIB_FLAGS}])
    AC_MSG_RESULT([Using Python lib dir:       ${PYTHON_LIBDIR}])
    AC_DEFINE_UNQUOTED([HAVE_PYTHON],$HAVE_PYTHON,[Enables Python])
    AC_SUBST(HAVE_PYTHON)
    AC_SUBST(PYTHON_INCLUDE_FLAGS)
    AC_SUBST(PYTHON_LIB_FLAGS)
    AC_SUBST(PYTHON_LIBDIR)
    AC_SUBST(PYTHON_NAME)
else
    AC_MSG_RESULT([Python disabled])
fi

# R is optional
HAVE_R=0
USE_R=0
RCPP_PATH=0
RINSIDE_PATH=0
# Drop this?  Almost guaranteed to fail.
AC_ARG_ENABLE(r,
    AS_HELP_STRING([--enable-r],
                   [Enable calling R language]),
    [
      HAVE_R=1
      USE_R="/usr" # Acceptable default?
    ])

AC_ARG_WITH(rinside,
   AS_HELP_STRING([--with-rinside],
                   [Use this RInside directory]),
   [
      HAVE_R=1
      RINSIDE_PATH=${withval}
   ])

AC_ARG_WITH(rcpp,
   AS_HELP_STRING([--with-rcpp],
                   [Use this Rcpp directory]),
   [
      HAVE_R=1
      RCPP_PATH=${withval}
   ])

AC_ARG_WITH(r,
   AS_HELP_STRING([--with-r],
                   [Use this R directory]),
  [
      HAVE_R=1
      USE_R=${withval}
  ])

if [[ ${HAVE_R} == 1 ]]
then
  AC_MSG_RESULT([Adding to PATH: $USE_R/../../bin ])
  PATH=$USE_R/../../bin:$PATH

  AC_CHECK_FILE(${USE_R}/include/R.h, [],
        [AC_MSG_ERROR([Could not find R header!])])
  if [[ ${RINSIDE_PATH} == 0 ]]
  then
    AC_CHECK_PROG(RSCRIPT, Rscript, Rscript)
    if [[ ! -z ${RSCRIPT} ]]
    then
      R_LIB_PATHS=" ${USE_R} "
      # returned in order of precedence so we want to
      # reverse so that the last overrides the first
      R_LIB_PATHS+="$(Rscript -e 'cat(rev(.libPaths()))')"
      for p in $R_LIB_PATHS
      do
        AC_CHECK_FILE(${p}/RInside, [RINSIDE_PATH=${p}/RInside], [])
      done
    else
      RINSIDE_PATH=${USE_R}/RInside
    fi
  fi

  if [[ ${RCPP_PATH} == 0 ]]
  then
    AC_CHECK_PROG(RSCRIPT, Rscript, Rscript)
    if [[ ! -z ${RSCRIPT} ]]
    then
      R_LIB_PATHS="${USE_R} "
      # returned in order of precedence so we want to
      # reverse so that the last overrides the first
      R_LIB_PATHS+="$(Rscript -e 'cat(rev(.libPaths()))')"
      for p in $R_LIB_PATHS
      do
        AC_CHECK_FILE(${p}/Rcpp, [RCPP_PATH=${p}/Rcpp], [])
      done
    else
      RCPP_PATH=${USE_R}/Rcpp
    fi
  fi

  AC_CHECK_FILE(${RINSIDE_PATH}/include/RInside.h, [],
                 [AC_MSG_ERROR([Could not find RInside header!])])
  AC_CHECK_FILE(${RCPP_PATH}/include/Rcpp.h, [],
                 [AC_MSG_ERROR([Could not find Rcpp header!])])

  # C++ is only required if we enable R
  AC_PROG_CXX
  LD=${CXX}
fi # if HAVE_R
AC_DEFINE_UNQUOTED([HAVE_R],$HAVE_R,[Enables R])
AC_SUBST(HAVE_R)
AC_SUBST(USE_R)
AC_SUBST(RINSIDE_PATH)
AC_SUBST(RCPP_PATH)

# JVM scripting support: Disabled by default
HAVE_JVM_SCRIPT=0
USE_JVM_SCRIPT_HOME=0
AC_ARG_ENABLE(jvm-scripting,
    AS_HELP_STRING([--enable-jvm-scripting],
                   [Enable calling JVM scripting languages]),
    [
      HAVE_JVM_SCRIPT=1
      USE_JVM_SCRIPT_HOME=swift-t-jvm
    ])
AC_ARG_WITH(jvm-scripting,
   AS_HELP_STRING([--with-jvm-scripting],
                   [Use this JVM scripting plugin home directory]),
  [
      HAVE_JVM_SCRIPT=1
      USE_JVM_SCRIPT_HOME=${withval}
  ])
if (( ${HAVE_JVM_SCRIPT} ))
then
    AC_CHECK_FILE(${USE_JVM_SCRIPT_HOME}/src/swift-jvm.h, [],
                  [AC_MSG_ERROR([Could not find JVM scripting header!])])
    AC_MSG_RESULT([JVM scripting enabled])
else
    AC_MSG_RESULT([JVM scripting disabled])
fi

AC_DEFINE_UNQUOTED([HAVE_JVM_SCRIPT],$HAVE_JVM_SCRIPT,[Enables JVM scripting])
AC_SUBST(HAVE_JVM_SCRIPT)
AC_SUBST(USE_JVM_SCRIPT_HOME)

#JVM HOME
AC_SUBST(JVMHOME,"/usr/lib/jvm/java-8-oracle")
AC_ARG_WITH([jvm-home],
            [AS_HELP_STRING([--with-jvm-home],
               [Set up the jvm home directory (default: /usr/lib/jvm/java-8-oracle)])],
            [AC_SUBST(JVMHOME,$withval)],
            )

#JVM SWIFT-T LIBs
AC_SUBST(JVMLIB,$(pwd)"/swift-jvm/swift-jvm-build/target/swift-jvm-build-0.0.1-bin/swift-jvm/classes")
AC_ARG_WITH([swift-jvm-engine-lib],
            [AS_HELP_STRING([--with-swift-jvm-engine-lib],
               [Set up the swift jvm engine lib (default: classes)])],
            [AC_SUBST(JVMLIB,$withval)],
            )
# End of JVM scripting configuration

# Julia: optional
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
        USE_TCLSH_LOCAL=$( maint/find-tcl.sh ${withval} )
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
        program=$( which tclsh${TCLVER} 2>&1 )
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
      test -d ${LIB_DIR} || continue
      LIB_DIR=$( cd ${LIB_DIR} ; /bin/pwd ) # Canonicalize
      LIBTCL_PREFIX=libtcl${TCLVER}
      LIBTCL=${LIBTCL_PREFIX}.${TCL_LIB_SUFFIX}
      AC_MSG_CHECKING([for Tcl library at ${LIB_DIR}/${LIBTCL}])
      if [[ -r ${LIB_DIR}/${LIBTCL} ]]
      then
        AC_MSG_RESULT([yes])
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

AC_MSG_RESULT([using Tcl version: ${TCL_VERSION}])
AC_MSG_RESULT([Tcl shared library: ${USE_LIBTCL_SO}])
AC_MSG_RESULT([Tcl static library: ${USE_LIBTCL_A}])
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

# TCL_VERSION needed by find-tcl.sh
export TCL_VERSION

# Find tclsh binary name (may be tclsh8.5)
if [[ ${USE_TCLSH_LOCAL} == 0 ]]
then
    USE_TCLSH=$( maint/find-tcl.sh ${USE_TCL} )
    if [[ ${?} != 0 ]]
    then
        AC_MSG_ERROR([Could not find Tcl ${TCL_VERSION} binary in ${USE_TCL}!])
    fi
    USE_TCLSH_LOCAL=${USE_TCLSH}
else
    export NO_RUN=1
    USE_TCLSH=$( maint/find-tcl.sh ${USE_TCL} )
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
    AC_MSG_RESULT([HDF5 enabled])
else
    AC_MSG_RESULT([HDF5 disabled])
    AC_MSG_RESULT([Note: HDF5 is only required if you need Turbine's HDF features.])
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

AC_SUBST(LD)
AC_SUBST(CFLAGS)
AC_SUBST(LDFLAGS)

m4_include([../../dev/m4/syslibs.m4])

AC_MSG_RESULT([])
AC_MSG_RESULT([Preparing config files: please wait...])

AC_CONFIG_FILES(
        bin/turbine
        src/turbine/turbine-version.h
        src/turbine/module.mk
        src/util/module.mk
        src/util/debug-tokens.tcl
        src/tcl/module.mk
        src/tcl/adlb/module.mk
        src/tcl/blob/module.mk
        src/tcl/c-utils/module.mk
        src/tcl/mpe/module.mk
        src/tcl/jvm/module.mk
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
        scripts/submit/sge/module.mk
        scripts/submit/theta/module.mk
        scripts/turbine-config.sh
        scripts/turbine-build-config.sh
        tests/module.mk
        tests/runbin.zsh
	Makefile
)

        #         $USE_JVM_SCRIPT_HOME/src/module.mk

# Debian stuff: noops
AC_ARG_ENABLE([dependency-tracking])
AC_ARG_ENABLE([maintainer-mode])
AC_ARG_ENABLE([silent-rules])

AC_OUTPUT
