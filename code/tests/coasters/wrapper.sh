#!/bin/bash

echo "Current date : $(date)"
echo "Running on $(hostname -f)"
echo "Test output to stderr " 1>&2
env