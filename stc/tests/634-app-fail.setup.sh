dirname=`dirname $0`
testdir=`cd $dirname; pwd`

if [ ! -f 634-fail-app.sh ]; then
  ln -s ${testdir}/634-fail-app.sh .
fi
