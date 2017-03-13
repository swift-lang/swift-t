
# HELPERS.SH
# Bash helper functions

push()
{
  pushd $* 2>&1 > /dev/null
}

pop()
{
  popd $* 2>&1 > /dev/null
}
