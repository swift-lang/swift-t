#include <builtins.swift>

main {
    trace("workers",turbine_workers());
    trace("servers",adlb_servers());
    trace("engines",turbine_engines());
}
