#!/usr/bin/env zsh -f
set -eu

# MAKE DEBS
# Main user interface to make all Swift/T DEBs
# Warning: this installs the created DEBs

# Naming note:
# We are making a PACKAGE (PKG)
# The PKG_TYPE is deb-dev or deb-bin (not src or spack)
#    Thus, package names are DEB/{DEV,BIN}
#    Thus, the Makefile targets are deb or deb-dev
# The resulting files are *.deb or *-dev.deb
#    Thus, file names are {BIN,DEV}/DEB

# Options:
# -b : Make bundle containing all DEBs and an installer script
# -c : Clean DEBs

setopt pushdsilent nullglob
zparseopts -D -E b=B c=C

source turbine/code/scripts/helpers.zsh
source dev/get-versions.sh

# The TGZ to be constructed here:
TGZ=swift-t-debs-${SWIFT_T_VERSION}.tar.gz

# The DEB module names, types, Makefile targets, and versions
typeset -A MODULES DEBS TYPES VERSIONS
MODULES=(  c-utils exmcutils lb adlbx   turbine turbine stc stc     )
TYPES=(    c-utils -dev      lb -dev    turbine ""      stc ""      )
DEBS=(     c-utils deb-dev   lb deb-dev turbine deb-bin stc deb-bin )
VERSIONS=( c-utils ${CUTILS_VERSION}
           lb      ${ADLBX_VERSION}
           turbine ${TURBINE_VERSION}
           stc     ${STC_VERSION} )

if (( ${#C} ))
then
  for M in ${(@k)MODULES}
  do
    pushd ${M}/code
    N=${MODULES[${M}]}
    rm0 -v ${N}*.tar.gz ${N}*.deb
    rm0 -r .deb-work-*
    popd
  done
  return
fi

COMPLETE=0
START=${SECONDS}

cleanup()
{
  if (( ! COMPLETE ))
  then
    print
    print "Exiting early due to error: Cleaning up..."
    rm -rv $1
  else
    print "Normal exit."
    rm -r $1
  fi
}

if (( ${#B} ))
then
  WORK_DIR=${PWD}/$( mktemp -d make-debs.XXX )
  BUNDLE_DIR=${WORK_DIR}/swift-t-debs
  mkdir -pv ${BUNDLE_DIR}
  TRAPEXIT() { cleanup ${WORK_DIR} }
fi

for M in ${(@k)MODULES}
do
  print
  pushd ${M}/code
  print "Making DEB in ${PWD}..."
  uptodate configure configure.ac bootstrap || ./bootstrap
  uptodate Makefile configure || ./configure --enable-debian-build
  D=${DEBS[${M}]}
  @ make PKG_TYPE=${D} ${D}
  N=${MODULES[${M}]}
  T=${TYPES[${M}]}
  V=${VERSIONS[${M}]}
  DEB=${N}${T}_${V}_amd64.deb
  stat --format "" ${DEB} # Ensure file exists
  (( ${#B} )) && ln -t ${BUNDLE_DIR} ${DEB}
  sudo dpkg -i ${DEB}
  popd
done

if (( ${#B} ))
then
  m4 -D M4_CUTILS_VERSION=${CUTILS_VERSION}   \
     -D M4_ADLBX_VERSION=${ADLBX_VERSION}     \
     -D M4_TURBINE_VERSION=${TURBINE_VERSION} \
     -D M4_STC_VERSION=${STC_VERSION}         \
     dev/debian/install-debs.sh.m4 > ${BUNDLE_DIR}/install-debs.sh
  cp dev/debian/install-readme.md ${BUNDLE_DIR}/README.txt
  chmod u+x ${BUNDLE_DIR}/install-debs.sh
  pushd ${WORK_DIR}
  tar cfz ${TGZ} swift-t-debs
  popd
  mv -v ${WORK_DIR}/${TGZ} .
  # Report the product and its size to the user:
  du -h ${TGZ}
  COMPLETE=1
fi

STOP=${SECONDS}

print "TOOK:" $(( STOP - START ))
