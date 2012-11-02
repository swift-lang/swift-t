#!/bin/sh
if [ ! -f test.tmp ]; then
    echo "test.tmp was not created"
    exit 1
fi

contents=`cat test.tmp`
if [ "$contents" = "hello world!" ] ; then
    rm test.tmp
    exit 0
else
    echo "test.tmp did not have expected contents"
    exit 1
fi

