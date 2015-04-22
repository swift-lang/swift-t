#!/usr/bin/env bash


z_member_count=$(grep -E 'z member: [0-9]+' $TURBINE_OUTPUT | wc -l)
z_member_exp=2000

if [[ "$z_member_count" != $z_member_exp ]]; then
  echo "Expected z to have $z_member_exp members, but had $z_member_count"
  exit 1
fi
