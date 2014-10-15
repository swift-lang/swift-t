dirname=`dirname $0`
testdir=`cd $dirname; pwd`

script=6390-echostderr.sh
if [ ! -f ./${script} ]; then
  ln -s ${testdir}/${script} .
fi
