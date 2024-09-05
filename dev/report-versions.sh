#!/usr/bin/env zsh

THIS=${0:h:A}
source $THIS/get-versions.sh

Vs=(  CUTILS_VERSION
      ADLBX_VERSION
      TURBINE_VERSION
      STC_VERSION
      SWIFT_T_VERSION
   )

for V in $Vs
do
  printf "%-15s %6s\n" $V ${(P)V}
done




