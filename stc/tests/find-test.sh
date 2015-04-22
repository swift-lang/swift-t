#!/usr/bin/env bash
SCRIPT_DIR=`dirname $0`
SEARCH=$1
ls ${SCRIPT_DIR}/*.swift | nl | grep "$1"
