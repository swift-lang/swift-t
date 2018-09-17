
This directory contains shell scripts to configure and build Swift/T.

./init-settings.sh -
creates swift-t-settings.sh from
swift-t-settings.sh.template if swift-t-settings.sh does not exist.
Can be used by the user from the shell, but is also used by the other
build scripts.  It will not overwrite swift-t-settings.sh; if you want
to obtain a clean swift-t-settings.sh, you must delete it and then run
./init-settings.sh .

./build-swift-t.sh - build c-utils/adlb/turbine/stc from fresh source.
                     use 'build-swift-t.sh -h' for help

./build-cutils.sh
./build-lb.sh
./build-turbine.sh
./build-stc.sh -
These four scripts build their respective components individually.
They respect swift-t-settings.sh and the same flags as build-swift-t.sh

./build-mpi.sh - build MPICH from source

./build-tcl.sh - build Tcl from source
