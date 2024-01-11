
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
AC_INIT([Turbine], [turbine_version()], [woz@anl.gov])
AC_CONFIG_SRCDIR([src/turbine/turbine.c])
AC_CONFIG_HEADERS([config.h])

m4_include([../../dev/m4/ifblank.m4])
m4_include([../../dev/m4/tcl.m4])

TURBINE_VERSION=turbine_version()
AC_MSG_RESULT([Turbine version: ${TURBINE_VERSION}])
AC_SUBST(TURBINE_VERSION)

DEBIAN_BUILD=0
AC_ARG_ENABLE(debian-build,
  AS_HELP_STRING(
    [--enable-debian-build], [configure for a Debian package]),
    [DEBIAN_BUILD=1]
)
AC_SUBST(DEBIAN_BUILD)

if [[ ${prefix} == "NONE" ]]
then
  if (( ! DEBIAN_BUILD ))
  then
    AC_MSG_ERROR([You must provide --prefix])
  fi
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

AC_MSG_RESULT([using CC='$CC'])
if [[ "${CC}" != "" ]]
then
  AC_MSG_RESULT([which CC='$(which $CC)'])
fi
# Check for CC
AC_PROG_CC

# Report location of CC
USE_CC=$( which $CC )
AC_MSG_RESULT([using CC='$USE_CC'])
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
AC_CACHE_CHECK([for cp that supports -u], [ac_cv_path_HAVE_CP_U],
            [AC_PATH_PROGS_FEATURE_CHECK([HAVE_CP_U], [cp],
              [[cp_u_out=`cp -u /dev/null cp_u.token 2>&1 > /dev/null`
                test "$?" = 0 \
                && ac_cv_path_HAVE_CP_U=yes ac_path_HAVE_CP_U_found=: ]],
              [ac_cv_path_HAVE_CP_U=no])])
AC_SUBST([HAVE_CP_U], [$ac_cv_path_HAVE_CP_U])
[[ -w cp_u.token ]] && rm cp_u.token

# We prefer to use cp --parents for installation
AC_CACHE_CHECK([for cp that supports --parents], [ac_cv_path_cp_parents],
            [AC_PATH_PROGS_FEATURE_CHECK([HAVE_CP_PARENTS], [cp],
              [[cp_parents_out=`cp --parents /dev/null . > /dev/null 2>&1`
                test "$?" = 0 \
                && ac_cv_path_cp_parents=yes || ac_cv_path_cp_parents=no ]],
              [])])
# If cp --parents succeeded, we have a directory to remove
[[ -d dev ]] && rm -r dev
AC_SUBST([HAVE_CP_PARENTS], [$ac_cv_path_cp_parents])

# Checks for headers
AC_CHECK_HEADERS([fcntl.h inttypes.h limits.h malloc.h stddef.h])
AC_CHECK_HEADERS([stdint.h unistd.h sys/param.h sys/time.h])

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
AC_FUNC_ALLOCA
AC_FUNC_MALLOC
AC_FUNC_REALLOC
AC_FUNC_STRTOD
AC_CHECK_FUNCS([dup2 getcwd gettimeofday mallinfo memchr memset pow])
AC_CHECK_FUNCS([setenv strchr strdup strerror strlen strnlen strpbrk])
# These vary: strchrnul is on Linux but not Mac
#             strlcpy   is on Mac   but not Linux
AC_CHECK_FUNCS([strchrnul strlcpy])
AC_CHECK_FUNCS([strstr strtol strtoul strtoull uname])

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

AC_ARG_WITH(launcher,
    AS_HELP_STRING([--with-launcher],
                   [full path to job launcher (mpiexec, etc.)]),
    [USE_LAUNCHER=${withval}],
    [USE_LAUNCHER=$(which mpiexec)]
)
AC_CHECK_FILE($USE_LAUNCHER,
    [AC_MSG_RESULT([Using Turbine MPI launcher: $USE_LAUNCHER])],
    [AC_MSG_ERROR([Launcher not found: '$USE_LAUNCHER'])])

AC_CHECK_HEADER([mpi.h], [], [AC_MSG_ERROR([Could not find mpi.h!])])
AC_CHECK_LIB([mpi], [MPI_Init], [],
             [AC_MSG_ERROR([Could not find MPI library!])])

# Set MPI_IMPL
# Use the preprocessor to extract tokens that identify MPICH or OpenMPI:
HAVE_MPI_IMPL_OPENMPI=0
HAVE_MPI_IMPL_MPICH=0
MPI_OUTPUT=$( mktemp )
$CC -E $CPPFLAGS $CFLAGS maint/mpi_identification.c 2>&1 > $MPI_OUTPUT

if grep -q FOUND_MPICH $MPI_OUTPUT
then
  MPI_IMPL="MPICH"
elif grep -q FOUND_OpenMPI $MPI_OUTPUT
then
  MPI_IMPL="OpenMPI"
else
  MPI_IMPL="UNKNOWN"
fi
AC_MSG_RESULT([Setting MPI_IMPL: ${MPI_IMPL}])

# Set to flag for per-rank line numbers if supported by mpiexec:
MPI_LINE_OUTPUT_FLAG=""

if [[ $MPI_IMPL == "OpenMPI" ]]
then
  HAVE_MPI_IMPL_OPENMPI=1
  OMPI_MAJOR_VERSION=$( awk '$1 == "MAJOR-VERSION" {print $2}' \
                        $MPI_OUTPUT )
  OMPI_MINOR_VERSION=$( awk '$1 == "MINOR-VERSION" {print $2}' \
                         $MPI_OUTPUT )
  AC_MSG_RESULT(
    [OpenMPI version is $OMPI_MAJOR_VERSION . $OMPI_MINOR_VERSION])
  if (( OMPI_MAJOR_VERSION > 3 || OMPI_MINOR_VERSION >= 3 ))
  then
    # These versions support tagged output
    MPI_LINE_OUTPUT_FLAG="--tag-output"
  else
    MPI_LINE_OUTPUT_FLAG=""
  fi
elif [[ $MPI_IMPL == "MPICH" ]]
then
  HAVE_MPI_IMPL_MPICH=1
  MPI_LINE_OUTPUT_FLAG="-l"
else
  AC_MSG_WARN([No MPI implementation tokens found in mpi.h !])
fi

# Clean up temp file:
rm $MPI_OUTPUT

AC_DEFINE_UNQUOTED(HAVE_MPI_IMPL_OPENMPI,
                   ${HAVE_MPI_IMPL_OPENMPI},[True if using OpenMPI])
AC_DEFINE_UNQUOTED(HAVE_MPI_IMPL_MPICH,
                   ${HAVE_MPI_IMPL_MPICH},[True if using MPICH])

# Write out MPI settings
AC_SUBST(USE_MACHINE)
AC_SUBST(USE_LAUNCHER)
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
        AC_MSG_RESULT([OK])
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
    AC_DEFINE_UNQUOTED([PYTHON_NAME],"$PYTHON_NAME",[Provides Python Name])
else
    AC_MSG_RESULT([Python disabled])
fi

# R is optional
HAVE_R=0          # If we have R, this is 1
USE_R=0           # If we have R, this is R.home()
RCPP_PATH=0
RINSIDE_PATH=0

AC_ARG_ENABLE(r,
    AS_HELP_STRING([--enable-r],
                   [Enable calling R language]),
    [
      HAVE_R=1
      AC_CHECK_PROG(RSCRIPT, Rscript, Rscript)
      USE_R=$( Rscript -e "cat(R.home())" )
      AC_MSG_RESULT([Found R at: $USE_R])
      AC_MSG_RESULT([Using Rscript: $( which Rscript ) ])
    ])

AC_ARG_WITH(r,
   AS_HELP_STRING([--with-r],
                   [Use this R directory, the one returned by R.home() ]),
  [
      HAVE_R=1
      USE_R=$withval
      R_BIN=$USE_R/../../bin
      if [[ -d $R_BIN ]]
      then
        R_BIN=$( cd $R_BIN ; /bin/pwd )
        AC_MSG_RESULT([Adding to PATH: $R_BIN ])
        PATH=$R_BIN:$PATH
      fi
      AC_CHECK_PROG(RSCRIPT, Rscript, Rscript)
      AC_MSG_RESULT([Using Rscript: $( which Rscript )])
      if Rscript -e '"R_IS_OK"' | grep R_IS_OK
      then
        AC_MSG_RESULT(["Rscript works."])
      else
        AC_MSG_ERROR([Cannot execute simple Rscript program.])
      fi
  ])

AC_ARG_WITH(rinside,
   AS_HELP_STRING([--with-rinside],
                   [Use this RInside directory]),
   [
      HAVE_R=1
      RINSIDE_PATH=$withval
   ])

AC_ARG_WITH(rcpp,
   AS_HELP_STRING([--with-rcpp],
                   [Use this Rcpp directory]),
   [
      HAVE_R=1
      RCPP_PATH=$withval
   ])

if (( $HAVE_R ))
then
  R_HEADER_FOUND=0
  DEFAULT_R_INCLUDE=$USE_R/include
  AC_CHECK_FILE($DEFAULT_R_INCLUDE/R.h,
                [ R_INCLUDE=$DEFAULT_R_INCLUDE ; R_HEADER_FOUND=1 ])
  if (( ! R_HEADER_FOUND ))
  then
    R_INCLUDE=$( Rscript -e "cat(R.home('include'))" )
    AC_CHECK_FILE($R_INCLUDE/R.h, [ R_HEADER_FOUND=1 ])
  fi
  dnl # Another way to find the R.h location:
  dnl if (( ! R_HEADER_FOUND ))
  dnl then
  dnl   R_INCLUDE=$( Rscript -e "cat(Sys.getenv('R_INCLUDE_DIR'))" )
  dnl   AC_CHECK_FILE($R_INCLUDE/R.h, [ R_HEADER_FOUND=1 ])
  dnl fi
  if (( ! R_HEADER_FOUND ))
  then
    AC_MSG_ERROR("Could not find R header!")
  fi
  AC_MSG_RESULT([Using R include: $R_INCLUDE])

  if [[ ${RINSIDE_PATH} == 0 ]]
  then
    if [[ ! -z ${RSCRIPT} ]]
    then
      R_LIB_PATHS=" ${USE_R} "
      # returned in order of precedence so we want to
      # reverse so that the last overrides the first
      R_LIB_PATHS+="$( Rscript -e 'cat(rev(.libPaths()))' )"
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
    if [[ ! -z ${RSCRIPT} ]]
    then
      R_LIB_PATHS="${USE_R} "
      # returned in order of precedence so we want to
      # reverse so that the last overrides the first
      R_LIB_PATHS+="$( Rscript -e 'cat(rev(.libPaths()))' )"
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
AC_SUBST(R_INCLUDE)
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

ENABLE_STATIC_PKG=0
AC_ARG_ENABLE(static-pkg,
    AS_HELP_STRING([--enable-static-pkg],
       [Create statically linkable Turbine Tcl package with Tcl source.
        Default: disabled]),
    [
       if test "$enableval" = no ; then
         ENABLE_STATIC_PKG=0
       elif test "$enable_val" = yes ; then
         ENABLE_STATIC_PKG=1
       fi
    ])
AC_SUBST(ENABLE_STATIC_PKG)

ENABLE_STATIC=0
AC_ARG_ENABLE(static,
    AS_HELP_STRING([--enable-static],
             [Create static libraries.
              Default: disabled]),
  [
       if test "$enableval" = no ; then
         ENABLE_STATIC=0
       fi
    ])
AC_SUBST(ENABLE_STATIC)

# Need ar and ranlib to build static libraries
AC_CHECK_PROG(AR, ar, ar, AC_MSG_ERROR(Cannot find ar))
AC_PROG_RANLIB

# Configure Tcl:
# Defaults:
# These will be full paths to a tclsh.  They may be the same.
# This is the runtime (compute node) tclsh
USE_TCLSH=0
# This is the build system tclsh
USE_TCLSH_LOCAL=0

AC_ARG_WITH(tclsh,
            AS_HELP_STRING(
               [--with-tclsh],
               [full path to runtime tclsh (optional override)]),
            [
             # No error checks- let the user be creative
             AC_MSG_RESULT([overriding Tcl shell to: ${withval}])
             USE_TCLSH=${withval}
            ])

# Allow user to override build-system-compatible Tcl shell
USE_TCLSH_LOCAL=$USE_TCLSH  # Default
AC_ARG_WITH(tclsh-local,
            AS_HELP_STRING(
               [--with-tclsh-local],
               [full path to local tclsh on build system
                (defaults to --with-tclsh)]),
            [
             # Use error checks- this tclsh must work on build system:
             AC_CHECK_FILE(${withval})
             AC_MSG_RESULT([overriding local Tcl shell to: ${withval}])
             USE_TCLSH_LOCAL=${withval}
            ])

# If one is set but not the other, make them agree:
if [[ $USE_TCLSH == 0 ]] && [[ $USE_TCLSH_LOCAL != 0 ]]
then
    USE_TCLSH=$USE_TCLSH_LOCAL
    AC_MSG_RESULT(
      [assigning to Tcl shell from local Tcl shell: ${USE_TCLSH}])
fi
if [[ $USE_TCLSH_LOCAL == 0 ]] && [[ $USE_TCLSH != 0 ]]
then
    USE_TCLSH_LOCAL=$USE_TCLSH
    AC_MSG_RESULT(
      [assigning to local Tcl shell from Tcl shell: ${USE_TCLSH_LOCAL}])
fi

# If not USE_TCLSH_LOCAL not set, find any tclsh in PATH:
if [[ $USE_TCLSH_LOCAL == 0 ]]
then
    AC_MSG_RESULT([No local tclsh set; checking PATH ...])
    if which tclsh 2>&1 > /dev/null
    then
        USE_TCLSH_LOCAL=$( which tclsh )
    elif which tclsh8.7 2>&1 /dev/null
    then
        USE_TCLSH_LOCAL=$( which tclsh8.7 )
    elif which tclsh8.6 2>&1 /dev/null
    then
        USE_TCLSH_LOCAL=$( which tclsh8.6 )
    else
        AC_MSG_ERROR([Could not find a local tclsh!])
    fi
    AC_MSG_RESULT([Found local tclsh in PATH at ${USE_TCLSH_LOCAL}])
fi

# Find tclConfig.sh from the local tclsh as a default:
# We modified tcl.m4 to refer to TCL_FOUND if needed
TCL_FOUND=$( $USE_TCLSH_LOCAL maint/find-tclConfig.tcl )
AC_MSG_RESULT([Found local tclConfig.sh at ${TCL_FOUND}])

# This sets $TCL_BIN_DIR to the directory containing tclConfig.sh
#      allowing for user --with-tcl
TEA_PATH_TCLCONFIG()
# This loads the Tcl settings as variables TCL_*
. $TCL_BIN_DIR/tclConfig.sh

USE_TCLSH=$TCL_PREFIX/bin/tclsh$TCL_VERSION
AC_MSG_RESULT([using Turbine Tcl executable: ${USE_TCLSH}])

AC_SUBST(TCL_VERSION)
AC_SUBST(USE_TCLSH)
AC_SUBST(USE_TCLSH_LOCAL)
AC_SUBST(TCL_INCLUDE_SPEC)
AC_SUBST(TCL_LIB_SPEC)
# Tcl library dependencies for static build
TCL_LIB_SPEC_EXTRA="${TCL_LIBS}"
AC_SUBST(TCL_LIB_SPEC_EXTRA)

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

USE_NVC=0
AC_ARG_ENABLE(nvc,
              AS_HELP_STRING(
                    [--enable-nvc],
                    [Enable NVIDIA NVC flags]),
              [
                USE_NVC=1
              ])
AC_SUBST(USE_NVC)

m4_include([../../dev/m4/ax_lib_hdf5.m4])
AX_LIB_HDF5()
if [[ "${HAVE_HDF5}" == 1 ]]
then
    AC_MSG_RESULT([HDF5 enabled])
else
    AC_MSG_RESULT([HDF5 disabled])
    AC_MSG_RESULT([Note: HDF5 is only required if you need Turbine's HDF features.])
fi

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
        Makefile
        bin/turbine
        bin/turbine-pilot
        scripts/turbine-config.sh
        scripts/turbine-build-config.sh
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
        src/tcl/launch/module.mk
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
        scripts/submit/lsf/module.mk
        scripts/submit/pbs/module.mk
        scripts/submit/psij/module.mk
        scripts/submit/slurm/module.mk
        scripts/submit/sge/module.mk
        scripts/submit/theta/module.mk
        tests/module.mk
        tests/runbin.zsh
)

        #         $USE_JVM_SCRIPT_HOME/src/module.mk

# Debian stuff: noops
AC_ARG_ENABLE([dependency-tracking])
AC_ARG_ENABLE([maintainer-mode])
AC_ARG_ENABLE([silent-rules])

AC_OUTPUT
