

namespace eval my_pkg {
    proc f { x y } {
        return [ expr $x + $y ]
    }
}
