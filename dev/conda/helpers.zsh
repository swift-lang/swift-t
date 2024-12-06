
# HELPERS ZSH
# Helpers for Anaconda stuff

checksum()
{
  # Use redirection to suppress filename in program output
  local PKG=$1
  print "checksum(CONDA_PLATFORM=$CONDA_PLATFORM)" $PKG
  if [[ $CONDA_PLATFORM =~ osx-* ]] {
    md5 -r < $PKG
  } else {
    md5sum < $PKG
  }
}
