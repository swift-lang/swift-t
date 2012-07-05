#!/bin/sh

# set -x

DIR=$1
C_FILE=$2
D_FILE=$3
shift 3
CFLAGS=${*}

exec ${CC} -M -MG ${CFLAGS} ${C_FILE} | \
  sed -e "s@^\(.*\)\.o:@$DIR/\1.d $DIR/\1.o:@" > ${D_FILE}
