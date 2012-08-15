
# XLB: CONFIGURE

AC_INIT()
AC_PREREQ(2.63)
AC_CONFIG_AUX_DIR([.])
AC_CONFIG_HEADER(config.h src/mpe-settings.h)

echo "Configuring ADLB with $ac_configure_args"

CC=${CC:-mpicc}

USE_MAC="no"
if [[ $( uname ) == "Darwin" ]]
then
        AC_MSG_RESULT([detected Mac.])
        USE_MAC="yes"
fi
AC_SUBST(USE_MAC)

# Checks for programs
AC_PROG_CC
AC_CHECK_PROG(AR, ar, ar, ;)
AC_PROG_RANLIB

# We prefer to use cp -u for installation
AC_CACHE_CHECK([for cp that supports -u], [ac_cv_path_cp_u],
    [AC_PATH_PROGS_FEATURE_CHECK([CP_U], [cp],
         [[cp_u_out=`cp -u /dev/null /dev/stdout 2>&1 | cat > /dev/null`
           [[ ${?} == 0 ]]        \
           && ac_cv_path_cp_u=yes \
           || ac_cv_path_cp_u=no  ]],
         [])])
AC_SUBST([CP_U], [$ac_cv_path_cp_u])

AC_ARG_ENABLE(mpe,
        [--enable-mpe - Enable MPE. The default is disabled.],
        enable_mpe=yes , )
if test "$enable_checkMPE" = "yes" ; then
    ADLB_ENABLE_MPE=yes
else
    ADLB_ENABLE_MPE=no
fi

if [[ ${ADLB_ENABLE_MPE} == "yes" ]]
then
    AC_MSG_CHECKING( [for the MPE compiler flags] )
    MPILOG_FLAG="-mpe=mpilog"
    LOG_FLAG="-mpe=log"
    savedCFLAGS="$CFLAGS"
    CFLAGS="$CFLAGS $MPILOG_FLAG"
    AC_LINK_IFELSE([AC_LANG_PROGRAM([#include "mpe.h"],[MPE_Init_log();])],
                   adlb_ok=yes,adlb_ok=no)
    CFLAGS="$savedCFLAGS"
    if test "$adlb_ok" = "yes" ; then
        AC_MSG_RESULT([$MPILOG_FLAG and $LOG_FLAG])
    else
        MPILOG_FLAG="-mpilog"
        LOG_FLAG="-log"
        savedCFLAGS="$CFLAGS"
        CFLAGS="$CFLAGS $MPILOG_FLAG"
        AC_LINK_IFELSE([AC_LANG_PROGRAM([#include "mpe.h"],[MPE_Init_log();])],
                       adlb_ok=yes,adlb_ok=no)
        CFLAGS="$savedCFLAGS"
        if test "$adlb_ok" = "yes" ; then
            AC_MSG_RESULT([$MPILOG_FLAG and $LOG_FLAG])
        else
            MPILOG_FLAG=""
            LOG_FLAG=""
            ADLB_ENABLE_MPE=no
            AC_MSG_RESULT([none])
            dnl If there isn't any MPE compiler flag,
            dnl should check for mpe.h, -llmpe and -lmpe.
        fi
    fi
else
     AC_MSG_RESULT([Not using MPE])
fi
AC_SUBST(MPILOG_FLAG)
AC_SUBST(LOG_FLAG)
AC_SUBST(ADLB_ENABLE_MPE)
AC_DEFINE_UNQUOTED(ADLB_ENABLE_MPE,
                   "$ADLB_ENABLE_MPE", [Using MPE: yes/no])

AC_ARG_ENABLE(fast,
              AS_HELP_STRING(
                    [--enable-fast],
                    [Performance mode]),
              [
                CFLAGS="-O3 -D NDEBUG"
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

AC_ARG_WITH(c-utils,
                 AS_HELP_STRING(
                    [--with-c-utils],
                    [location of ExM c-utils]),
                   [
                    USE_C_UTILS=0
                    AC_CHECK_FILE( ${withval}/include/c-utils.h,
                                   [USE_C_UTILS=1], [])
                    if test "$USE_C_UTILS" = 0 ; then
                        AC_MSG_ERROR([Could not find ExM c-utils in $withval])
                    fi
                    AC_MSG_RESULT([using ExM c-utils in $withval ...])
                    AC_DEFINE_UNQUOTED([C_UTILS_LOCATION],
                                       "$withval", [ExM c-utils location])
                    USE_C_UTILS=$withval
                 ],
                 [
                   AC_MSG_ERROR([Not found: ExM c-utils])
                   USE_C_UTILS=0
                 ])
AC_SUBST(USE_C_UTILS)

AC_OUTPUT(Makefile)
AC_OUTPUT(src/module.mk)
