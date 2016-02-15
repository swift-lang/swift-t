
# SYSLIBS.M4

# We take system libraries out of arguments to the linker
# to prevent system libraries from pre-empting custom user
# library locations.
# We do not currently have a solution for this on the Mac. (#88)
# Cf. https://code.google.com/p/exm-issues/issues/detail?id=546

AC_MSG_NOTICE([Generating system-libs.txt])

AC_PATH_PROG([LDCONFIG],[ldconfig],[no],["$PATH:/sbin"])
if [[ ${LDCONFIG} == no ]]
then
    AC_MSG_NOTICE([\
Could not find ldconfig. \
The linker may use system directories. \
See /dev/m4/syslib.m4 for more information.])
fi

if [[ ${USE_MAC} == "no" ]]
then
    # This does not work on the Mac
    ${LDCONFIG} -v 2>/dev/null | grep -vP '\t' | cut -d : -f 1 > system-libs.txt
else
     touch system-libs.txt
fi
