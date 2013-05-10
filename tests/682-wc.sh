#!/bin/bash

# WC
# Redirection script

INPUT=$1
OUTPUT=$2

source helpers.sh

nonempty INPUT
nonempty OUTPUT

wc -l ${INPUT} > ${OUTPUT}
