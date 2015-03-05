#!/bin/bash
set -eu

# Obtain the Turbine build configuration variables
TURBINE=/homes/wozniak/sfw/fusion/compute/turbine-static
source ${TURBINE}/scripts/turbine-build-config.sh

# Generate hello.tic
stc hello.swift
# Bundle hello.tic and Turbine into hello_main.c
mkstatic.tcl hello.manifest -c hello_main.c

# Compile hello_main.c and link as standalone, static executable
CFLAGS=-std=gnu99
gcc -c ${CFLAGS} ${TURBINE_INCLUDES} hello_main.c
mpicc -static  -o hello.x hello_main.o ${TURBINE_LIBS} ${TURBINE_RPATH}
