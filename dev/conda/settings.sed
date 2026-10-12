
# SETTINGS.SED
# sed substitutions for swift-t-settings.sh - see build-generic.sh

# Point Turbine at the launcher and tclsh in the host $PREFIX (provided
# by the meta.yaml host: section: mpich and tk).  $PREFIX is expanded when
# swift-t-settings.sh is sourced at build time; because these live in the
# host prefix (not the build env) Conda relocates them into the install
# environment, so the installed turbine-config.sh is relocatable.
s@^MPI_VERSION=.*@&\nLAUNCHER=$PREFIX/bin/mpiexec\nTCLSH=$PREFIX/bin/tclsh8.6@

s@SWIFT_T_PREFIX=.*@SWIFT_T_PREFIX=$PREFIX/swift-t@
# Enable Python AND pin PYTHON_EXE to the host $PREFIX python (relocatable).
# swift-t-settings.sh has no uncommented PYTHON_EXE="" line to anchor on, so
# append the assignment after ENABLE_PYTHON (same trick as LAUNCHER/TCLSH).
# Without PYTHON_EXE set, build-turbine.sh passes --enable-python and Turbine
# auto-detects $BUILD_PREFIX/bin/python, baking a non-relocatable _build_env
# path into turbine-config.sh (TURBINE_USE_PYTHON) -> "python in PATH does
# not match the Python used at compile time".  python is in the meta.yaml
# host: section, so $PREFIX/bin/python exists at build and Conda relocates it.
s@ENABLE_PYTHON=0@ENABLE_PYTHON=1\nPYTHON_EXE="$PREFIX/bin/python"@
s@PARALLELISM=.*@PARALLELISM=4@
s@ENABLE_CONDA_LINUX=.*@ENABLE_CONDA_LINUX="$ENABLE_CONDA_LINUX"@
m4_ifelse(m4_getenv(ENABLE_R),`1',m4_include(settings-R.sed))

# Local Variables:
# mode: sh;
# End:
