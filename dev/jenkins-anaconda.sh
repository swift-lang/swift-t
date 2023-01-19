
# JENKINS ANACONDA SH
# Install Anaconda for GCE Jenkins

set -eux

renice --priority 19 --pid $$

rm -fv Anaconda3-2022.10-Linux-x86_64.sh
wget --no-verbose https://repo.anaconda.com/archive/Anaconda3-2022.10-Linux-x86_64.sh
nice -n 19 bash Anaconda3-2022.10-Linux-x86_64.sh -b -f -p $WORKSPACE/sfw/Anaconda3

PATH=$WORKSPACE/sfw/Anaconda3/bin:$PATH
which pip
pip install deap
