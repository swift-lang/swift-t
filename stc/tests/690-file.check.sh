#!/bin/bash

# Look for filename output at end of line
grep -q "alice.txt$" ${TURBINE_OUTPUT}

rm -f alice.txt
