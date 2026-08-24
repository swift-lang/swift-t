dirname=`dirname $0`
testdir=`cd $dirname; pwd`

# Read directly rather than piping from cat: run-tests.zsh sources this
# script, and in zsh the SIGPIPE that head causes in cat becomes the exit
# status of the whole script (141), failing the setup
head -c 10000 /dev/urandom > 635-rand.tmp
