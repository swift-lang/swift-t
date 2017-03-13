
# HELPERS.SH
# Minimal POSIX shell helper functions

crash()
{
  echo    > /dev/stderr
  echo $1 > /dev/stderr
  exit 1
}

push()
{
  pushd $@ 2>&1 > /dev/null
}

pop()
{
  popd $@ 2>&1 > /dev/null
}
