Turbine is a runtime library that supports distributed execution of
task-parallel data-flow-based applications. It depends directly on:
 * lb, a distributed MPI-based load balancer and data-store
 * c-utils, for data structures and other utilities

Turbine is developed as part of the ExM project.  For further details on
ExM, visit http://exm.xstack.org

Building
========
See the Turbine - Build page for notes:

https://sites.google.com/site/exmproject/development/turbine---build

To build:

Type

./setup.sh
./configure --with-adlb=<PATH/TO/ADLB>
            --with-mpi=<PATH/TO/MPICH>
            --with-tcl=<PATH/TO/TCL>
make package

Note:

PATH/TO/ADLB  : Points to source tree
PATH/TO/MPICH : Points to installed location
PATH/TO/TCL   : Points to installed location

Remember to use config.status for speed

Contact
=======
Justin Wozniak: wozniak@mcs.anl.gov
