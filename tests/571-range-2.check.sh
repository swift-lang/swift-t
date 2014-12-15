#!/usr/bin/env bash

y_count=$(grep -E 'y=[0-9]+\.[0-9]+' $TURBINE_OUTPUT | wc -l)
y_exp=10

if [[ "$y_count" != $y_exp ]]; then
  echo "Expected z to have $y_exp members, but had $y_count"
  exit 1
fi

z_count=$(grep -E 'z=[0-9]+\.[0-9]+' $TURBINE_OUTPUT | wc -l)
z_exp=134

if [[ "$z_count" != $z_exp ]]; then
  echo "Expected z to have $z_exp members, but had $z_count"
  exit 1
fi

a_count=$(grep -E 'a=[0-9]+\.[0-9]+' $TURBINE_OUTPUT | wc -l)
a_exp=1

if [[ "$a_count" != $a_exp ]]; then
  echo "Expected a to have $a_exp members, but had $a_count"
  exit 1
fi


