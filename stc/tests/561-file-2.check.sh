#!/bin/sh
if [ ! -f 561-out1.txt ]; then
    echo "561-out1.txt was not created"
    exit 1
fi

if [ ! -f 561-out2.txt ]; then
    echo "561-out2.txt was not created"
    exit 1
fi

contents=`cat 561-out1.txt`
if [ "$contents" = "hello world!" ] ; then
    rm 561-out1.txt
else
    echo "561-out1 did not have expected contents"
    exit 1
fi


contents=`cat 561-out2.txt`
if [ "$contents" = "bye world!" ] ; then
    rm 561-out2.txt
else
    echo "561-out2 did not have expected contents"
    exit 1
fi
