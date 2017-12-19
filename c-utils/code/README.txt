c-utils is collection of data structures and other utility functions.

c-utils is developed as part of the Swift-T project.
For further details, visit http://swift-lang.org/Swift-T

Building
========
To build:

Type

./bootstrap
./configure
make

Creates a library in lib/

To use:

#include the headers from src/ that you want to use.
link with -L c-utils/lib -l exmcutils

Configure options:

export CFLAGS="-O0 -g"
for debugging

export CFLAGS="-D VALGRIND"
to use valgrind (cf. lookup3.c)

Usage: See About.txt

Contact
=======
Justin Wozniak: wozniak@mcs.anl.gov
