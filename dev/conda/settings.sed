
# SETTINGS.SED
# sed substitutions for swift-t-settings.sh - see build-generic.sh

# TODO: May need to set TCLSH and LAUNCHER here

s@SWIFT_T_PREFIX=.*@SWIFT_T_PREFIX=$PREFIX/swift-t@
s@ENABLE_PYTHON=0@ENABLE_PYTHON=1@
s@PYTHON_EXE=""@PYTHON_EXE="$PYTHON"@
s@PARALLELISM=.*@PARALLELISM=4@
s@ENABLE_CONDA_LINUX=.*@ENABLE_CONDA_LINUX="$ENABLE_CONDA_LINUX"@
m4_ifelse(m4_getenv(ENABLE_R),`1',m4_include(settings-R.sed))

# Local Variables:
# mode: sh;
# End:
