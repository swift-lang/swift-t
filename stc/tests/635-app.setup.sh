dirname=`dirname $0`
testdir=`cd $dirname; pwd`

cat /dev/urandom | head -c 10000 > 635-rand.tmp
