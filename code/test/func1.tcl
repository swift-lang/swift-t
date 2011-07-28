
package require turbine 0.1

proc f { inputs outputs } {
    set tmp [ turbine_new ]
    set tmpfile "tmp-$tmp.txt"
    turbine_file $tmp $tmpfile
    set data1 [ lindex $inputs 0 ]
    set filename1 [ turbine_filename $data1 ]
    set data2 [ lindex $outputs 0 ]
    set filename2 [ turbine_filename $data2 ]
    set rule1 [ turbine_new ]
    turbine_rule $rule1 "f" $inputs $tmp  "cp $filename1 $tmpfile"
    set rule2 [ turbine_new ]
    turbine_rule $rule2 "f" $tmp $outputs "cp $tmpfile $filename2"
}

proc rules { } {
    turbine_file 0 /dev/null
    turbine_file 1 A.txt
    turbine_file 2 B.txt

    turbine_rule 1 A  {   } { 1 } { touch A.txt }
    turbine_rule 2 F1 { 1 } { 2 } { tp: f { 1 } { 2 } }
}

turbine_init

rules

turbine_engine

turbine_finalize

puts OK
