#!/usr/bin/env zsh

set -x

# Note we are using ZSH quoting here:
WORDS=( hello world /bin "/home/user/evil path/d2/d1" /usr/bin )
for W in ${WORDS}
do
  grep "trace: ${W}" ${TURBINE_OUTPUT} || exit 1
done

exit 0
