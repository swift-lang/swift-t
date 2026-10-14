
# HELPERS ZSH
# Helpers for Anaconda stuff

checksum()
{
  # Use redirection to suppress filename in program output
  # Emits only the hash.
  local PKG=$1
  local OUT
  if [[ $CONDA_PLATFORM =~ osx-* ]] {
    OUT=$( md5 -r < $PKG )
  } else {
    OUT=$( md5sum < $PKG )
  }
  print ${OUT%% *}
}
