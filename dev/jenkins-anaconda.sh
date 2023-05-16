
# JENKINS ANACONDA SH
# Install Anaconda for GCE Jenkins

set -eux

renice --priority 19 --pid $$

rm -fv Anaconda3-2022.10-Linux-x86_64.sh
rm -fr $WORKSPACE/sfw/Anaconda3
wget --no-verbose https://repo.anaconda.com/archive/Anaconda3-2022.10-Linux-x86_64.sh
bash Anaconda3-2022.10-Linux-x86_64.sh -b -p $WORKSPACE/sfw/Anaconda3

PATH=$WORKSPACE/sfw/Anaconda3/bin:$PATH
which pip
pip install deap

# python-csv is broken - 2023-05-16
# pip install python-csv
