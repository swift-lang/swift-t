#!/bin/sh

# Sorter for list-events.x

LOG=$1

./list-events.x ${LOG} | sort -n
