
package provide my_pkg 0.1

namespace eval my_pkg {
    proc f { x y } {
        return [ expr $x + $y ]
    }
}
