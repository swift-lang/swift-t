#!/bin/bash
set -eu

# BUILD-SPACKS
# Puts all Spack packages in given output directory

if [[ ${#} != 1 || $1 == "-h" ]]
then
  echo "usage: build-spacks.sh <OUTPUT>"
  echo "packages will be created in the OUTPUT directory"
  exit 1
fi

OUTPUT_ARG=$1
if [[ ${OUTPUT_ARG:0:1} == "/" ]]
then
  OUTPUT=$OUTPUT_ARG
else
  OUTPUT=$PWD/$OUTPUT_ARG
fi

THIS=$( dirname $0 )
SWIFT_T=$( cd $THIS/.. ; /bin/pwd )
cd $SWIFT_T

source $SWIFT_T/dev/helpers.sh
source $SWIFT_T/dev/get-versions.sh

MK_SPACK="$SWIFT_T/dev/mk-src-tgz.sh spack"
# This is relative to the current module:
FILE_LIST=maint/file-list.zsh

mkdir -pv $OUTPUT

CUTILS_TGZ=$OUTPUT/exmcutils-$CUTILS_VERSION.tar.gz
ADLBX_TGZ=$OUTPUT/adlbx-$ADLBX_VERSION.tar.gz
TURBINE_TGZ=$OUTPUT/turbine-$TURBINE_VERSION.tar.gz
STC_TGZ=$OUTPUT/stc-$STC_VERSION.tar.gz

push c-utils/code
$MK_SPACK $CUTILS_TGZ exmcutils $CUTILS_VERSION $FILE_LIST
pop

push lb/code
$MK_SPACK $ADLBX_TGZ adlbx $ADLBX_VERSION $FILE_LIST
pop

push turbine/code
$MK_SPACK $TURBINE_TGZ turbine $TURBINE_VERSION $FILE_LIST
pop

push stc/code
$MK_SPACK $STC_TGZ stc $STC_VERSION $FILE_LIST
pop

echo
echo "Built all Spack packages."
du -h $OUTPUT/*.tar.gz
