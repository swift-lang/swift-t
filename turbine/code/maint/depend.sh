#!/bin/sh

# DEPEND.SH
# Creates a Makefile dependency statement
#         for include into the Makefile

# For C file in directory DIR, creates a .d file
# Uses a standard GCC feature to obtain the dependencies
# then does a sed operation to make the .d file dependent
# on the dependencies so that if they change the .d file
# is updated.  This avoids the need for a 'make deps' step.

# Usage:
# depend.sh [-d DROP] DIR C_FILE D_FILE CFLAGS...
# DROP may be used to remove a dependency

set -eu
# set -x

DEP_DROP=

while getopts "d:" OPT
do
  case ${OPT} in
    d) DEP_DROP="${DEP_DROP} ${OPTARG}" ;;
  esac
done
shift $(( OPTIND-1 ))

DIR=$1
C_FILE=$2
D_FILE=$3
shift 3
CFLAGS=${*}

# Note that we define DEPS so that we can detect that
# we are doing a dependency scan and can skip problematic
# code sections, i.e., C++
${CC} -D DEPS -M -MG ${CFLAGS} ${C_FILE} | \
   sed -e "s@^\(.*\)\.o:@$DIR/\1.d $DIR/\1.o:@" > ${D_FILE}

for DEP in ${DEP_DROP}
do
  sed -i s@${DEP}@@ ${D_FILE}
done
