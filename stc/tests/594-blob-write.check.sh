#!/bin/sh -eu

od -t f8 A.blob | grep -q 0.5 
rm A.blob

