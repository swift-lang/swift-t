#!/bin/bash

[[ -s "test2.out" ]] && echo "test-2.out present : PASS" ||  echo "FAIL"
[[ -f "test2.err" ]] && echo "test-2.err present : PASS" ||  echo "FAIL"



