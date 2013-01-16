
# EXM ADLB/X (XLB)
# configure.ac
# Compile this with: ./setup.sh
# After compiling this, get help with: ./configure --help

define([adlb_version],
    regexp(esyscmd(cat version.txt),[\([.0-9]*\)],[\1]))

AC_INIT([XLB], [adlb_version()], [wozniak@mcs.anl.gov])
AC_PREREQ(2.63)
AC_CONFIG_AUX_DIR([.])
AC_CONFIG_HEADER(config.h src/mpe-settings.h)

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
  echo "CC not set: setting CC=mpicc"
  export CC=mpicc
fi
which ${CC} >& /dev/null
if [[ ${?} != 0 ]]
then
  echo "CC=${CC}: not found in PATH!"
  exit 1
fi

echo "using CC=$( which ${CC} )"

echo "PREFIX: ${prefix}"
SOFTWARE=$( cd $(dirname ${prefix}) ; /bin/pwd )
if [[ ${?} == 0 ]]
then
  echo "SOFTWARE: ${SOFTWARE}"
else
  SOFTWARE=0
fi

# Checks for programs
AC_PROG_CC
AC_CHECK_PROG(AR, ar)
AC_PROG_RANLIB

# Checks for functions
AC_CHECK_FUNCS([memset stpcpy strdup strtol])
AC_FUNC_ALLOCA
AC_FUNC_MALLOC

# Checks for headers
# Note: Mac OS does not have malloc.h
AC_CHECK_HEADERS([inttypes.h limits.h malloc.h memory.h stddef.h])
AC_CHECK_HEADERS([stdlib.h string.h unistd.h])
AC_HEADER_STDBOOL

# Checks for compiler features
AC_C_INLINE
AC_C_RESTRICT

# We prefer to use cp -u for installation
AC_CACHE_CHECK([for cp that supports -u], [ac_cv_path_cp_u],
    [AC_PATH_PROGS_FEATURE_CHECK([CP_U], [cp],
         [[cp_u_out=`cp -u /dev/null /dev/stdout 2>&1 | cat > /dev/null`
           [[ ${?} == 0 ]]        \
           && ac_cv_path_cp_u=yes \
           || ac_cv_path_cp_u=no  ]],
         [])])
AC_SUBST([CP_U], [$ac_cv_path_cp_u])

ADLB_ENABLE_MPE=no
AC_ARG_WITH(mpe,
  AS_HELP_STRING([--with-mpe],
                 [Enable MPE, set directory with MPE library]),
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
    AC_CHECK_FILE(${withval}/libmpe.so)
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
  AS_HELP_STRING([--with-log-trace-mpi], [trace MPI calls]),
  ENABLE_LOG_TRACE_MPI=1)

AC_DEFINE_UNQUOTED(ENABLE_LOG_DEBUG, [${ENABLE_LOG_DEBUG}],
                   [Whether to use debug logging])
AC_DEFINE_UNQUOTED(ENABLE_LOG_TRACE, [${ENABLE_LOG_TRACE}],
                   [Whether to use trace logging])
AC_DEFINE_UNQUOTED(ENABLE_LOG_TRACE_MPI, [${ENABLE_LOG_TRACE_MPI}],
                   [Whether to trace MPI calls])

AC_CONFIG_FILES(Makefile
                src/module.mk
                src/adlb-version.h
                tests/module.mk)
AC_OUTPUT
