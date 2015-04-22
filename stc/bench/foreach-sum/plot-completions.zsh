#!/bin/zsh

EXM=${HOME}/exm

TIME_COMPLETIONS=${EXM}/scripts/mpe/time-counts.tcl
CLOG=adlb.clog2
RANK=-1

export MPE_EVENTS="set1rA"
${TIME_COMPLETIONS} ${CLOG} ${RANK} > cA.data
[[ ${?} == 0 ]] || { print ERROR ; return 1 }
printf OK

export MPE_EVENTS="set1rB"
${TIME_COMPLETIONS} ${CLOG} ${RANK} > cB.data
printf .

export MPE_EVENTS="sum"
${TIME_COMPLETIONS} ${CLOG} ${RANK} > cS.data
print .

swift_plotter.zsh -s foreach-sum.{cfg,eps} c[ABS].data
