
# EXM ADLB/X (XLB)
# configure.ac
# Compile this with: ./bootstrap
# After compiling this, get help with: ./configure --help

define([adlb_version],
    regexp(esyscmd(cat version.txt),[\([.0-9]*\)],[\1]))

m4_include([../../dev/m4/ifblank.m4])

AC_INIT([XLB],[adlb_version()],[woz@anl.gov])
AC_PREREQ([2.68])
AC_CONFIG_MACRO_DIR([m4])
AC_CONFIG_AUX_DIR([.])
AC_CONFIG_HEADERS([config.h src/mpe-settings.h])

ADLB_VERSION=adlb_version()
AC_MSG_RESULT([ADLB/X version: ${ADLB_VERSION}])
AC_SUBST(ADLB_VERSION)

if [[ ${prefix} == ${PWD} ]]
then
  AC_MSG_ERROR([Your --prefix should not be the same as PWD: $PWD])
fi

echo ${PWD} > source.txt

USE_MAC="no"
if [[ $( uname ) == "Darwin" ]]
then
        AC_MSG_RESULT([detected Mac.])
        USE_MAC="yes"
fi
AC_SUBST(USE_MAC)

# Provide CC=mpicc by default
if [[ "${CC}" == "" ]]
then
  AC_MSG_RESULT([CC not set: setting CC=mpicc])
  export CC=mpicc
fi
which ${CC} >& /dev/null
if [[ ${?} != 0 ]]
then
  echo "CC=${CC}: not found in PATH!"
  exit 1
fi

# Report location of CC
USE_CC=$( which $CC )
AC_MSG_RESULT([using  CC=$USE_CC])

AC_MSG_RESULT([PREFIX:   ${prefix}])  # Space to align path with above
SOFTWARE=$( dirname ${prefix} 2>&1 )
SOFTWARE=$( test -d ${SOFTWARE} && cd ${SOFTWARE} 2>&1 && /bin/pwd )
if [[ ${?} == 0 ]]
then
  # Space to align path with above
  AC_MSG_RESULT([SOFTWARE: ${SOFTWARE}])
else
  SOFTWARE=0
fi

# Checks for programs
AC_PROG_CC

AC_TYPE_INT32_T
AC_TYPE_INT64_T
AC_TYPE_UINT32_T
AC_TYPE_UINT64_T
AC_TYPE_OFF_T
AC_TYPE_PID_T
AC_TYPE_SSIZE_T

# Checks for functions
AC_CHECK_FUNCS([clock_gettime memchr memmove memset pow])
AC_CHECK_FUNCS([setenv stpcpy strdup strtol strerror uname])
AC_FUNC_ALLOCA
AC_FUNC_MALLOC
AC_FUNC_REALLOC

# Checks for headers
# Note: Mac OS does not have malloc.h
AC_CHECK_HEADERS([fcntl.h inttypes.h limits.h malloc.h memory.h ])
AC_CHECK_HEADERS([stddef.h stdint.h])
AC_CHECK_HEADERS([stdlib.h string.h unistd.h])
AC_HEADER_STDBOOL

# Checks for compiler features
AC_C_INLINE
AC_C_RESTRICT

m4_include([../../dev/m4/syslibs.m4])

# We prefer to use cp -u for installation
AC_CACHE_CHECK([for cp that supports -u], [ac_cv_path_cp_u],
            [AC_PATH_PROGS_FEATURE_CHECK([CP_U], [cp],
              [[cp_u_out=`cp -u /dev/null cp_u.token 2>&1 > /dev/null`
                test "$?" = 0 \
                && ac_cv_path_cp_u=yes || ac_cv_path_cp_u=no ]],
              [])])
AC_SUBST([CP_U], [$ac_cv_path_cp_u])
[[ -r cp_u.token ]] && rm cp_u.token

ADLB_ENABLE_MPE=no
AC_ARG_WITH(mpe,
  AS_HELP_STRING([--with-mpe],
                 [Enable MPE, set MPE directory]),
                 [ADLB_ENABLE_MPE=yes
                 USE_MPE=${withval}], [])
AC_SUBST(USE_MPE)
AC_MSG_RESULT([using MPE: ${ADLB_ENABLE_MPE}])

# From now on, make the user put -mpe=mpilog or -mpilog or -log in
# CFLAGS and LDFLAGS.
# I cannot figure this out. -Justin
# On the BG/P, with Anthony's install_ibm_qpic, use
#    CFLAGS='-mpilog'
#    LDFLAGS='-mpilog -L /dir' where dir contains libmpe.so
if [[ "${ADLB_ENABLE_MPE}" == "yes" ]]
then
    FOUND=0
    AC_CHECK_FILE(${withval}/lib/libmpe.so,,
                  AC_MSG_ERROR([Could not find MPE library!]))
fi

AC_SUBST(ADLB_ENABLE_MPE)
AC_SUBST(MPE_FLAGS)
AC_DEFINE_UNQUOTED(ADLB_ENABLE_MPE,
                   "$ADLB_ENABLE_MPE", [Using MPE: yes/no])

AC_ARG_ENABLE(fast,
              AS_HELP_STRING(
                    [--enable-fast],
                    [Performance mode]),
              [
                CFLAGS="${CFLAGS} -O3 -D NDEBUG"
              ])

USE_XLC=0
AC_ARG_ENABLE(xlc,
              AS_HELP_STRING(
                    [--enable-xlc],
                    [Support IBM XLC on BG/P]),
              [
                USE_XLC=1
              ])
AC_SUBST(USE_XLC)

DEBIAN_BUILD=0
AC_ARG_ENABLE(debian-build,
  AS_HELP_STRING(
    [--enable-debian-build], [configure for a Debian package]),
    [DEBIAN_BUILD=1]
)

USE_C_UTILS=0
AC_ARG_WITH(c-utils,
  AS_HELP_STRING(
    [--with-c-utils], [location of ExM c-utils]),
  [
    AC_CHECK_FILE(${withval}/include/c-utils.h,
                  [USE_C_UTILS=1], [])
    if [[ ${USE_C_UTILS} == 0 ]]
    then
      AC_MSG_ERROR([Could not find ExM c-utils in ${withval}])
    fi
    AC_MSG_RESULT([using ExM c-utils in $withval ...])
    AC_DEFINE_UNQUOTED([C_UTILS_LOCATION],
                       ${withval}, [ExM c-utils location])
    USE_C_UTILS=${withval}
  ],
  [
    [[ ${SOFTWARE} == 0 ]] && AC_MSG_ERROR([Not found: ExM c-utils])

    # Are we building a Debian package?
    if (( ${DEBIAN_BUILD} ))
    then
        USE_C_UTILS=/usr
    else # We are doing a source installation
        location=${SOFTWARE}/c-utils
        AC_CHECK_FILE(${location}/include/c-utils.h,
                      [USE_C_UTILS=${location}], [])
    fi
    [[ ${USE_C_UTILS} == 0 ]] && AC_MSG_ERROR([Not found: ExM c-utils])
])
AC_SUBST(USE_C_UTILS)

dnl Log settings.  See debug.h
ENABLE_LOG_DEBUG=0
ENABLE_LOG_TRACE=0
ENABLE_LOG_TRACE_MPI=0

AC_ARG_ENABLE(log-debug,
  AS_HELP_STRING([--enable-log-debug], [enable debug logging]),
  ENABLE_LOG_DEBUG=1)
AC_ARG_ENABLE(log-trace,
  AS_HELP_STRING([--enable-log-trace], [enable trace logging]),
  ENABLE_LOG_TRACE=1)
AC_ARG_ENABLE(log-trace-mpi,
  AS_HELP_STRING([--enable-log-trace-mpi], [trace MPI calls]),
  ENABLE_LOG_TRACE_MPI=1)

AC_DEFINE_UNQUOTED(ENABLE_LOG_DEBUG, [${ENABLE_LOG_DEBUG}],
                   [Whether to use debug logging])
AC_DEFINE_UNQUOTED(ENABLE_LOG_TRACE, [${ENABLE_LOG_TRACE}],
                   [Whether to use trace logging])
AC_DEFINE_UNQUOTED(ENABLE_LOG_TRACE_MPI, [${ENABLE_LOG_TRACE_MPI}],
                   [Whether to trace MPI calls])

ADLB_MPI_VERSION=3
AC_ARG_ENABLE(mpi-2,
  AS_HELP_STRING([--enable-mpi-2],
                 [Force compatibility with MPI 2.
                  Disables parallel tasks.
                  If not given, MPI version 3 is enabled.]),
  [ADLB_MPI_VERSION=2], [])
AC_DEFINE_UNQUOTED(ADLB_MPI_VERSION, [${ADLB_MPI_VERSION}],
                   [MPI version])

# if user wants to build with MPI 3,
# check the reported MPI_VERSION number
if (( ${ADLB_MPI_VERSION} >= 3 ))
then
AC_MSG_CHECKING([MPI version])
${CC} ${CFLAGS} -o maint/mpi_version3_test maint/mpi_version3_test.c ${LDFLAGS}
[[ ${?} != 0 ]] && AC_MSG_ERROR(
      [MPI_VERSION >= 3 is required for parallel tasks!
                   You must 1) re-configure with --enable-mpi-2
                               which disables parallel tasks; or
                            2) use an MPI 3 implementation])
rm -f maint/mpi_version3_test
fi
AC_MSG_RESULT([MPI_VERSION == ${ADLB_MPI_VERSION}])

XLB_ENABLE_XPT=no
AC_ARG_ENABLE(checkpoint,
  AS_HELP_STRING([--enable-checkpoint],
                 [Enable checkpoint functionality (Default: disabled)]),
                 [
      if [[ "$enableval" == yes ]]
      then
        XLB_ENABLE_XPT=yes
      elif [[ "$enableval" == no ]]
      then
        XLB_ENABLE_XPT=no
      else
        echo "Invalid enable-checkpoint value: $enableval"
        exit 1
      fi], [])
AC_SUBST(XLB_ENABLE_XPT)

if [[ ${XLB_ENABLE_XPT} == yes ]]
then
  AC_MSG_RESULT([ADLB checkpoints enabled])
  ENABLE_ZLIB=0
  ZLIB_HOME=
  AC_FUNC_FSEEKO
  m4_include([../../dev/m4/ax_check_zlib.m4])
  AX_CHECK_ZLIB([ENABLE_ZLIB=1],
                AC_MSG_ERROR([Could not find zlib! (required for checkpoint)]))
fi
AC_SUBST(ZLIB_HOME)
AC_SUBST(ENABLE_ZLIB)

ENABLE_SHARED=1
AC_ARG_ENABLE(shared,
     [  --enable-shared         Enable shared library. Default: yes ],
     [
       if test "$enableval" = no ; then
         ENABLE_SHARED=0
       fi
    ])
AC_SUBST(ENABLE_SHARED)

ENABLE_STATIC=1
AC_ARG_ENABLE(static,
     [  --enable-static         Enable static library. Default: yes ],
     [
       if test "$enableval" = no ; then
         ENABLE_STATIC=0
       fi
    ])
AC_SUBST(ENABLE_STATIC)

if [[ "$ENABLE_STATIC" = 1 ]]; then
  # Need ar and ranlib to link
  AC_CHECK_PROG(AR, ar, ar, AC_MSG_ERROR(Cannot find ar))
  AC_PROG_RANLIB
fi

ENABLE_DEV=0
AC_ARG_ENABLE(dev,
    AS_HELP_STRING([--enable-dev],
                   [Turn on more warnings for developers.
                    Default: disabled]),
    ENABLE_DEV=1)
AC_SUBST(ENABLE_DEV)

AC_SUBST(CFLAGS)
AC_SUBST(LDFLAGS)

# Debian stuff: noops
AC_ARG_ENABLE([dependency-tracking])
AC_ARG_ENABLE([maintainer-mode])
AC_ARG_ENABLE([silent-rules])

AC_CONFIG_FILES(Makefile
                src/module.mk
                src/adlb-version.h
                tests/module.mk)
AC_OUTPUT
