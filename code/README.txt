
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

