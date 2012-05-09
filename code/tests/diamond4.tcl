
package require turbine 0.0.1

# Use turbine lang functionality

turbine::program {

    function touch { d } {
        # Like Swift @ syntax
        shell touch [@ d]
    }

    main {
        declare file f1 "test/data/A.txt"
        declare file f2 "test/data/B.txt"
        declare file f3 "test/data/C.txt"
        declare file f4 "test/data/D.txt"

        when ready {    }    { touch f1 } creates { f1 }
        when ready { f1 }    { touch f2 } creates { f2 }
        when ready { f1 }    { touch f3 } creates { f3 }
        when ready { f2 f3 } { touch f4 } creates { f4 }
    }
}
