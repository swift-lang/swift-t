dirname=`dirname $0`
testdir=`cd $dirname; pwd`

echo "Hello World" > helloworld.txt
if [ ! -f 631-app-cat.sh ]; then
  ln -s ${testdir}/631-app-cat.sh .
fi
