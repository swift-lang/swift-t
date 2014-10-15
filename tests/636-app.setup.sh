dirname=`dirname $0`
testdir=`cd $dirname; pwd`

if [ ! -f 636-echo.sh ]; then
  ln -s ${testdir}/636-echo.sh .
fi
