#!/bin/sh -e

./build.sh

mpiexec -n 4 ./controller.x
