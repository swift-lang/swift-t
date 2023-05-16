
# JENKINS ANACONDA SH
# Install Anaconda for GCE Jenkins

set -eux

renice --priority 19 --pid $$

pwd
ls

MINICONDA=Miniconda3-py39_22.11.1-1-Linux-x86_64.sh

rm -fv $MINICONDA
rm -fr $WORKSPACE/sfw/Miniconda-build
rm -fr $WORKSPACE/sfw/Miniconda-install

wget --no-verbose https://repo.anaconda.com/archive/$MINICONDA
bash $MINICONDA -b -p $WORKSPACE/sfw/Miniconda-build
bash $MINICONDA -b -p $WORKSPACE/sfw/Miniconda-install

PATH=$WORKSPACE/sfw/Miniconda-build/bin:$PATH

source "$WORKSPACE/sfw/Miniconda-build/etc/profile.d/conda.sh"
conda activate base

dev/conda/setup-conda.sh

# PATH=$WORKSPACE/sfw/Miniconda-install/bin:$PATH
