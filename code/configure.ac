
AC_INIT()
AC_PREREQ(2.63)
AC_CONFIG_AUX_DIR([.])
AC_CONFIG_HEADER(adlb_conf.h)

echo "Configuring ADLB with $ac_configure_args"

AC_ARG_ENABLE( echo,
[--enable-echo                      - Turn on strong echoing.
                                     The default is enable=no.],
set -x )

CC=${CC:-mpicc}
F77=${F77:-mpif90}
dnl Don't set CFLAGS here so AC_PROG_CC could add -g automatically.
# CFLAGS=${CFLAGS}
# FFLAGS=${FFLAGS}
MAKE=${MAKE:-make}

dnl Fixup for make


dnl If we are relying on vpath and no vpath is set, then we must exit
if test ! -s adlb.h -a -z "$VPATH" ; then
    AC_MSG_ERROR( [No virtual MAKE path command found.
        You may need to set your make command
        The GNU make (sometimes available as gnumake) can be used.] )
fi

dnl CPRP is the version of cp that accepts -r and -p arguments.
MKDIR=mkdir
CPRP=cp
RM=rm
MV=mv
AC_SUBST(MKDIR)
AC_SUBST(CPRP)
AC_SUBST(RM)
AC_SUBST(MV)

AC_ARG_ENABLE( strict,
[--enable-strict                    - Turn on strict compilation testing
                                     when using gcc],,enable_strict=no )
dnl
AC_ARG_ENABLE( f77,
[--enable-f77                       - Compile routines that require a Fortran
                                     compiler.  The default is enable=yes.], ,
enable_f77=yes )
if test "$enable_f77" = "yes" ; then
    ADLB_BUILD_FORTRAN=yes
else
    ADLB_BUILD_FORTRAN=no
fi

AC_ARG_ENABLE( checkMPI,
[--enable-checkMPI                  - Turn on/off the linking test for the MPI
                                     include files, MPI libraries and CC
                                     compiler. The default is enable=yes.], ,
enable_checkMPI=yes )
dnl
AC_ARG_ENABLE( checkMPE,
[--enable-checkMPE                  - Turn on/off the linking test for the MPE
                                     include files, MPE libraries and CC
                                     compiler. The default is enable=yes.],
enable_checkMPE=yes , )
if test "$enable_checkMPE" = "yes" ; then
    ADLB_BUILD_LOGGING=yes

else
    ADLB_BUILD_LOGGING=no
fi

dnl
dnl  Set the Fortran Include Library Leading Characters
dnl
AC_ARG_WITH( finc_path_leader,
[--with-finc_path_leader=FINC_PATH_LEADER - Specify a Fortran include path
                                     leader for compilation,
                                     default value is -I.],
FINC_PATH_LEADER="$withval" )

dnl
AC_ARG_WITH( flib_path_leader,
[--with-flib_path_leader=FLIB_PATH_LEADER - Specify a Fortran library path
                                     leader for linking, default value is -L.],
FLIB_PATH_LEADER="$withval" )

dnl
AC_ARG_WITH( config-args,
[--with-config-args=filename        - Specify configure argument file that
                                     contains the values of variables that
                                     configure reads, e.g. CC,F77,CFLAGS,FFLAGS.
                                     If the filename does not begin with
                                     / (absolute path), . or .. (relative path),
                                     the filename will be assumed to be
                                     $top_srcdir/configargs/<filename>.cfg.], ,
with_config_args=no )
dnl
if test "$with_config_args" != "no" ; then
    case "$with_config_args" in
        /*|../*|./*)
            config_args_file="$with_config_args"
            ;;
        *)
            config_args_file="$srcdir/configargs/$with_config_args.cfg"
            ;;
    esac
    if test -s "$config_args_file" ; then
        AC_MSG_RESULT([Reading the configure arguments in ${config_args_file}.])
        . $config_args_file
        # Export all the variables in $config_args_file
        # i.e. CC, F77, CFLAGS, FFLAGS and friends...
        config_args_vars=`grep -v '^#' $config_args_file | sed -e 's/=.*//g'`
        for var in $config_args_vars ; do
            eval value=\$"$var"
            echo "Exporting $var=$value ..."
            export $var
        done
    else
        AC_MSG_WARN([Non-existent ${config_args_file}.])
    fi
fi



AC_PROG_CC
AC_CHECK_PROG(AR, ar, ar, ;)
AC_PROG_RANLIB



dnl  We need to check that this has worked.  The autoconf macro is broken
AC_PROG_CPP
if test "$CPP" = "/lib/cpp" -a ! -x /lib/cpp ; then
    AC_MSG_ERROR( [configure could not find a working C preprocessor] )
fi

if test "$ADLB_BUILD_LOGGING" = "yes" ; then
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
            ADLB_BUILD_LOGGING=no
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
AC_SUBST(ADLB_BUILD_LOGGING)

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

AC_OUTPUT( Makefile )
