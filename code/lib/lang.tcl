
# Turbine language

namespace eval turbine {

    namespace export program

    proc program { body } {

        ::eval $body

        global env
        init $env(TURBINE_ENGINES) $env(ADLB_SERVERS)
        start rules
        finalize
    }

    proc main { body } {
        proc rules { } $body
    }
}
