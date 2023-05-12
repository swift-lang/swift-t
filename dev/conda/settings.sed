
# SETTINGS.SED
# sed substitutions for swift-t-settings.sh - see build-generic.sh

s@SWIFT_T_PREFIX=.*@SWIFT_T_PREFIX=$PREFIX/swift-t@
s@ENABLE_PYTHON=0@ENABLE_PYTHON=1@
s@PYTHON_EXE=""@PYTHON_EXE="$PYTHON"@
s@PARALLELISM=.*@PARALLELISM=4@
m4_ifelse(getenv(ENABLE_R),`1',m4_include(settings-R.sed))

# Local Variables:
# mode: sh;
# End:
