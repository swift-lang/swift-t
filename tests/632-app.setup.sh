dirname=`dirname $0`
testdir=`cd $dirname; pwd`

echo 'one
two
three
four
five
six' > lines.txt
if [ ! -f 632-split.sh ]; then
  ln -s ${testdir}/632-split.sh .
fi
