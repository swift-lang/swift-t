#!/bin/zsh

print
print "Building Debian package..."
declare PWD

if (( USE_TRUNK ))
then
  cd exm-trunk
else
  cd exm-${EXM_VERSION}
fi

# C-UTILS

package_c_utils()
{

  if  [[ ! -d c-utils-${C_UTILS_VERSION} ]]
  then
    ln -sv c-utils c-utils-${C_UTILS_VERSION}
    exitcode
  fi
  tar cfzh c-utils_${C_UTILS_VERSION}.orig.tar.gz c-utils-${C_UTILS_VERSION}
  exitcode "tar failed!"

  pushd c-utils-${C_UTILS_VERSION}
  exitcode

  declare PWD

# Create Debian files
  mkdir -p debian
  cp -uv NOTICE debian/copyright
  {
    print "c-utils (${C_UTILS_VERSION}) unstable; urgency=low"
    print
    print "  * Release ${C_UTILS_VERSION}"
    print
    print -- " -- wozniak <wozniak@mcs.anl.gov>  $(date -R)"
  } > debian/changelog
  {
    print "#!/usr/bin/make -f"
    print "%:"
    print "\tdh \$@"
  } > debian/rules

  print
  print "Running debuild ..."
  debuild
  exitcode "debuild failed!"
  print "debuild done."

  popd

  C_UTILS_DEB=c-utils_${C_UTILS_VERSION}_i386.deb
  [[ -f ${C_UTILS_DEB} ]]
  exitcode "Failed to create package!"

  du -h ${C_UTILS_DEB}

  return 0
}

package_adlb()
{
  if  [[ ! -d adlb-${ADLB_VERSION} ]]
  then
    ln -sv lb adlb-${ADLB_VERSION}
    exitcode
  fi
  tar cfzh adlb_${ADLB_VERSION}.orig.tar.gz adlb-${ADLB_VERSION}
  exitcode "tar failed!"

  pushd adlb-${ADLB_VERSION}
  exitcode

  declare PWD

# Create Debian files
  mkdir -p debian
  cp -uv NOTICE debian/copyright
  {
    print "adlb (${ADLB_VERSION}) unstable; urgency=low"
    print
    print "  * Release ${ADLB_VERSION}"
    print
    print -- " -- wozniak <wozniak@mcs.anl.gov>  $(date -R)"
  } > debian/changelog
  {
    print "#!/usr/bin/make -f"
    print "%:"
    print "\tdh \$@"
  } > debian/rules

  print
  print "Running debuild ..."
  debuild
  exitcode "debuild failed!"
  print "debuild done."

  popd

  ADLB_DEB=adlb_${ADLB_VERSION}_i386.deb
  [[ -f ${ADLB_DEB} ]]
  exitcode "Failed to create package!"

  du -h ${ADLB_DEB}
  return 0
}

package_c_utils
exitcode

# package_adlb
# exitcode

return 0
