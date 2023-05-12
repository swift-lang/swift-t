
# SED substitutions for swift-t-settings.sh - see build.sh

s@SWIFT_T_PREFIX=.*@SWIFT_T_PREFIX=$PREFIX/swift-t@
s@ENABLE_PYTHON=0@ENABLE_PYTHON=1@
s@PYTHON_EXE=""@PYTHON_EXE="$PYTHON"@
s@PARALLELISM=.*@PARALLELISM=4@

# Local Variables:
# mode: sh;
# End:
