# settings-R.sed: START
# Extra Swift/T build settings for R
s@ENABLE_R=0@ENABLE_R=1@
s@R_INSTALL=.*@R_INSTALL="$R_HOME"@
# Local Variables:
# mode: sh;
# End:
# settings-R.sed: STOP
