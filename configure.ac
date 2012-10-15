
# EXM C-UTILS
# configure.ac
# Compile this with: ./setup.sh
# After compiling this, get help with: ./configure --help

define([c_utils_version],
       regexp(esyscmd(cat version.txt),[\([.0-9]*\)],[\1]))

AC_PREREQ([2.62])
AC_INIT([EXMCUTILS], [c_utils_version()], [wozniak@mcs.anl.gov])
AC_CONFIG_SRCDIR([src/table.c])
AC_CONFIG_HEADERS([c-utils-config.h])

C_UTILS_VERSION=c_utils_version()
AC_MSG_RESULT([Turbine version: ${C_UTILS_VERSION}])
AC_SUBST(C_UTILS_VERSION)

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

# We prefer to use cp -u for installation
AC_CACHE_CHECK([for cp that supports -u], [ac_cv_path_cp_u],
    [AC_PATH_PROGS_FEATURE_CHECK([CP_U], [cp],
        [[cp_u_out=`cp -u /dev/null /dev/stdout 2>&1 | cat > /dev/null`
          [[ ${?} == 0 ]]              \
                && ac_cv_path_cp_u=yes \
                || ac_cv_path_cp_u=no  ]],
        [])])
AC_SUBST([CP_U], [$ac_cv_path_cp_u])

# This does not work:
# AC_PROG_INSTALL

# autoscan finds a false positive on C++
# AC_PROG_CXX

# Checks for header files
AC_CHECK_HEADERS([stddef.h stdint.h stdlib.h string.h])
AC_CHECK_HEADERS([sys/param.h] [sys/time.h])

AC_C_INLINE
AC_TYPE_UINT16_T
AC_TYPE_UINT32_T
AC_TYPE_UINT8_T

# Checks for typedefs, structures, and compiler characteristics
AC_HEADER_STDBOOL
AC_TYPE_SIZE_T

# Checks for library functions.
AC_FUNC_MALLOC

# Checks for libraries
AC_CHECK_FUNCS([gettimeofday strchr strnlen])

ENABLE_SHARED=
AC_ARG_ENABLE(shared,
     [  --enable-shared         Enable shared library ],
        ENABLE_SHARED=1)
AC_SUBST(ENABLE_SHARED)

ENABLE_FAST=
AC_ARG_ENABLE(fast,
     [  --enable-fast         Enable NDEBUG, -O3, etc. ],
        ENABLE_FAST=1
        CFLAGS="-O3 -DNDEBUG")
AC_SUBST(ENABLE_FAST)
AC_SUBST(CFLAGS)

AC_CONFIG_FILES(
	Makefile
        src/module.mk
        tests/module.mk
        src/c-utils.h
)

AC_OUTPUT
