
See the Turbine - Build page for notes: 

https://sites.google.com/site/exmproject/development/turbine---build

To build:

Type

./setup.sh
./configure --with-woztools=<PATH/TO/WOZTOOLS> \
            --with-adlb=<PATH/TO/ADLB>
            --with-mpi=<PATH/TO/MPICH>
            --with-tcl=<PATH/TO/TCL>
make package

Remember to use config.status for speed

