
# EXM C-UTILS
# configure.ac
# Compile this with: ./bootstrap
# Then, get help with: ./configure --help

define([c_utils_version],
       regexp(esyscmd(cat version.txt),[\([.0-9]*\)],[\1]))

AC_PREREQ([2.62])
AC_INIT([EXMCUTILS], [c_utils_version()], [woz@anl.gov])
AC_CONFIG_SRCDIR([src/table.c])
AC_CONFIG_HEADERS([c-utils-config.h])

C_UTILS_VERSION=c_utils_version()
AC_MSG_RESULT([c-utils version: ${C_UTILS_VERSION}])
AC_SUBST(C_UTILS_VERSION)

if [[ ${prefix} == "NONE" ]]
then
  if (( ! DEBIAN_BUILD ))
  then
    AC_MSG_ERROR([You must provide --prefix])
  fi
fi

if [[ ${prefix} == ${PWD} ]]
then
  AC_MSG_ERROR([Your --prefix should not be the same as PWD: $PWD])
fi

: ${CONDA_BUILD:=0}
if (( CONDA_BUILD ))
then
  echo "Anaconda-$CONDA_PY" > source.txt
else
  echo ${PWD} > source.txt
fi

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

USE_CC=$( which $CC )
AC_MSG_RESULT([using CC=$USE_CC])
AC_MSG_RESULT([CC version: $( $CC --version ) ])

# Checks for header files
AC_CHECK_HEADERS([inttypes.h stddef.h stdint.h stdlib.h string.h unistd.h])
AC_CHECK_HEADERS([sys/param.h] [sys/time.h])
AC_HEADER_STDBOOL

# Checks for typedefs, structures, and compiler characteristics
AC_C_INLINE
AC_TYPE_PID_T
AC_TYPE_INT64_T
AC_TYPE_UINT8_T
AC_TYPE_UINT16_T
AC_TYPE_UINT32_T
AC_TYPE_UINT64_T
AC_TYPE_OFF_T
AC_TYPE_SIZE_T

# Checks for library functions.
# Although autoscan 2.68 warns for AC_FUNC_STRNLEN, we do not use it
# because of known autoconf issues.  We do check for strnlen here:
AC_CHECK_FUNCS([error floor gettimeofday memchr memmove memset mkdir])
AC_CHECK_FUNCS([strchr strdup strerror strnlen strtol])
# These vary: strchrnul is on Linux but not Mac
#             strlcpy   is on Mac   but not Linux
AC_CHECK_FUNCS([strchrnul strlcpy])
AC_FUNC_ALLOCA
AC_FUNC_MALLOC
AC_FUNC_REALLOC

# This is needed by header files which cannot include c-utils-config.h
[[ ${ac_cv_func_strnlen} == "yes" ]]
SUBST_HAVE_STRNLEN=$(( ! ${?} ))
AC_SUBST([SUBST_HAVE_STRNLEN], ${SUBST_HAVE_STRNLEN})

# We prefer to use cp -u for installation
AC_CACHE_CHECK([for cp that supports -u], [ac_cv_path_cp_u],
            [AC_PATH_PROGS_FEATURE_CHECK([CP_U], [cp],
              [[cp_u_out=`cp -u /dev/null cp_u.token 2>&1 > /dev/null`
                test "$?" = 0 \
                && ac_cv_path_cp_u=yes || ac_cv_path_cp_u=no ]],
              [])])
AC_SUBST([CP_U], [$ac_cv_path_cp_u])
[[ -r cp_u.token ]] && rm cp_u.token

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

USE_XLC=0
AC_ARG_ENABLE(xlc,
              AS_HELP_STRING(
                    [--enable-xlc],
                    [Enable IBM XLC flags]),
              [
                USE_XLC=1
              ])
AC_SUBST(USE_XLC)

ENABLE_FAST=
AC_ARG_ENABLE(fast,
     [  --enable-fast         Enable NDEBUG, -O3, etc. ],
        ENABLE_FAST=1
        CFLAGS="-O3 -DNDEBUG")
AC_SUBST(ENABLE_FAST)

AC_SUBST(CFLAGS)
AC_SUBST(LDFLAGS)

# Debian stuff: noops
AC_ARG_ENABLE([dependency-tracking])
AC_ARG_ENABLE([maintainer-mode])
AC_ARG_ENABLE([silent-rules])

AC_CONFIG_FILES(
	Makefile
        src/module.mk
        tests/module.mk
        src/c-utils.h
)

AC_OUTPUT
