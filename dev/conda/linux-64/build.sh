
# linux-64 BUILD SH
# Simply calls build-generic.
# `conda build` calls this as Bash.

set -x
# THIS=$( cd $( dirname $0 ) ; /bin/pwd -P )
DEV_CONDA=$( cd $RECIPE_DIR/.. ; /bin/pwd -P )

$DEV_CONDA/build-generic.sh
