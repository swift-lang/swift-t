#!/bin/bash

[[ -s "test-2.out" ]] && echo "test-2.out present : PASS" ||  echo "FAIL"
[[ -f "test-2.err" ]] && echo "test-2.err present : PASS" ||  echo "FAIL"



