# settings-R.sed: START
# Extra Swift/T build settings for R

# swift-t-settings.sh has no uncommented R_INSTALL=... line to anchor on
# (only a commented example), so "s@R_INSTALL=.*@...@" silently no-ops and
# R_INSTALL is never set (same anchor trap as PYTHON_EXE -- see
# settings.sed).  Without R_INSTALL, build-turbine.sh passes --enable-r and
# turbine's configure finds R via plain "which Rscript", which can resolve
# to $BUILD_PREFIX's R ahead of the host R on PATH.  Append after the
# ENABLE_R anchor instead, like PYTHON_EXE.  $R_HOME is exported by
# build-generic.sh (from $PREFIX/bin/R) and expanded when
# swift-t-settings.sh is sourced at build time.
s@ENABLE_R=0@ENABLE_R=1\nR_INSTALL="$R_HOME"@

# Local Variables:
# mode: sh;
# End:
# settings-R.sed: STOP
