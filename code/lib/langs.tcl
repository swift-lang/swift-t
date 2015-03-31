
namespace eval turbine {

    proc python { code } {
        if { [ catch { set result [ python::eval $code ] } e ] } {
            puts $e
            turbine_error "Error in Python code!"
        }
        return $result
    }
}
