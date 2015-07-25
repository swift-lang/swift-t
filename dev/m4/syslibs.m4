AC_MSG_NOTICE([Generating system-libs.txt])
# We take system libraries out of arguments to the linker (#546)
if [[ ${USE_MAC} == "no" ]]
then
    # This does not work on the Mac
    ldconfig -v 2>/dev/null | grep -vP '\t' | cut -d : -f 1 > system-libs.txt
else
     touch system-libs.txt
fi
