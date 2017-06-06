
This directory contains shell scripts to configure and build Swift/T.

./build-all.sh - build c-utils/adlb/turbine/stc from fresh source.
Copies swift-t-settings.sh.template to swift-t-settings.sh and then
allows the user to modify swift-t-settings.sh before the build begins.

./rebuild-all.sh - rebuilds c-utils/adlb/turbine/stc from scratch without
reinitializing swift-t-settings.sh .  Cleans compiled code via make
clean and ant clean.

./fast-build-all.sh - does a build without reconfiguring.
Should only be used after running build-all.sh .

The build scripts have a common set of options (cf. options.sh)

* -B -- Force ./bootstrap to run for each module
* -c -- Do not make clean
* -m -- Do not run make (only configure)

./mpi_build.sh - build MPICH from source

./tcl_build.sh - build Tcl from source

./init-settings.sh - creates swift-t-settings.sh from
swift-t-settings.sh.template if swift-t-settings.sh does not exist.
Can be used by the user from the shell, but is also used by the other
build scripts.  It will not overwrite swift-t-settings.sh; if you want
to obtain a clean swift-t-settings.sh, you must delete it and then run
./init-settings.sh .
