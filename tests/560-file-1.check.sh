#!/bin/sh
if [ ! -f bob.txt ]; then
    echo "bob.txt was not created"
    exit 1
fi

contents=`cat bob.txt`
if [ "$contents" = "hello world!" ] ; then
    rm bob.txt alice.txt
    exit 0
else
    echo "bob.txt did not have expected contents"
    exit 1
fi

