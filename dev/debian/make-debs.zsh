#!/bin/zsh -f
set -eu

# MAKE DEBS
# Main user interface to make all Swift/T DEBs

# Options:
# -b : Make bundle
# -c : Clean DEBs

setopt pushdsilent nullglob
zparseopts -D -E b=B c=C

CUTILS_VERSION=$(  < c-utils/code/version.txt )
ADLBX_VERSION=$(   < lb/code/version.txt      )
TURBINE_VERSION=$( < turbine/code/version.txt )
STC_VERSION=$(     < stc/code/etc/version.txt )

typeset -A MODULES DEBS VERSIONS
MODULES=( c-utils exmcutils lb lb      turbine turbine stc stc )
DEBS=(    c-utils dev-deb   lb dev-deb turbine deb     stc deb )
VERSIONS=(    c-utils ${CUTILS_VERSION}
              lb      ${ADLBX_VERSION}
              turbine ${TURBINE_VERSION}
              stc     ${STC_VERSION} )

if (( ${#C} ))
then
  for M in ${(@k)MODULES}
  do
    pushd ${M}/code
    N=${MODULES[${M}]}
    rm -fv ${N}*.tar.gz ${N}*.deb
    popd
  done
  return
fi

START=${SECONDS}

if (( ${#B} ))
then
  T=$( mktemp -d make-debs.XXX )
  BUNDLE_DIR=${PWD}/${T}/swift-t-debs
  mkdir -pv ${BUNDLE_DIR}
fi

for M in ${(@k)MODULES}
do
  pushd ${M}/code
  D=${DEBS[${M}]}
  make ${D}
  (( ${#B} )) && ln -t ${BUNDLE_DIR} *.deb
  popd
done

if (( ${#B} ))
then
  m4 -D M4_CUTILS_VERSION=${CUTILS_VERSION}   \
     -D M4_ADLBX_VERSION=${ADLBX_VERSION}     \
     -D M4_TURBINE_VERSION=${TURBINE_VERSION} \
     -D M4_STC_VERSION=${STC_VERSION}         \
     dev/debian/install-debs.sh.m4 > ${BUNDLE_DIR}/install-debs.sh
  chmod u+x ${BUNDLE_DIR}/install-debs.sh

fi

STOP=${SECONDS}

print "TOOK:" $(( STOP - START ))
