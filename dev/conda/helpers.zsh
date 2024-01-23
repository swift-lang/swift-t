
# HELPERS ZSH
# Helpers for Anaconda stuff

checksum()
{
  # Use redirection to suppress filename in md5 output
  local PKG=$1
  if [[ $PLATFORM =~ osx-* ]] {
    md5 -r < $PKG
  } else {
    md5sum < $PKG
  }
}

print_environment()
{
}
