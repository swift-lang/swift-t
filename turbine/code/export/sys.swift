/*
 * Copyright 2013 University of Chicago and Argonne National Laboratory
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */

// SYS.SWIFT

#ifndef SYS_SWIFT
#define SYS_SWIFT

// argc, args, argv, argv_contains, argv_accept and argp - moved to
// builtins.swift so that they need no import, and so that the
// arguments() and flags() declarations work without importing sys

/* Model getenv as pure because it will be deterministic within
 * the context of a program
 */
@pure
(string s) getenv(string key) "turbine" "0.0.2" "getenv"
    [ "set <<s>> [ turbine::getenv_impl <<key>> ]" ];

// Do not optimize this- it is for tests
@dispatch=WORKER
(void v) sleep(float seconds) "turbine" "0.0.4" "sleep" [
  "if { <<seconds>> > 0 } { after [ expr {round(<<seconds>> * 1000)} ] }"
];

(int t) clock_seconds() "turbine" "0.1"
  [ "set <<t>> [ clock seconds ]" ];

// Millisecond-precision floating point time
(float t) clock() "turbine" "0.1.1"
  [ "set <<t>> [ expr {[ clock clicks -milliseconds ] / 1000.0 } ]" ];

CLOCK_FMT_ISO8601="%Y-%m-%dT%H:%M:%S";
CLOCK_FMT_RFC3339="%Y-%m-%d %H:%M:%S";

// Tcl clock format requires an int!
(string s) clock_format(string format, float|int t) "turbine" "0.1"
[ "set <<s>> [ clock format [ expr round(<<t>>) ] -format <<format>> ]" ];

// From: http://code.activestate.com/recipes/146035-largest-int-supported-by-a-platform-and-the-number
(int i) INT_MAX() "turbine" "0.0"
[ "set <<i>> [ expr [ regsub F [ format 0x%X -1 ] 7 ] ]" ];

(string o, int exit_code) system(string command[]) "turbine" "1.0"
[ "turbine::system <<command>> <<o>> <<exit_code>>" ];

(string o, int exit_code) system1(string command) "turbine" "1.0"
[ "turbine::system1 <<command>> <<o>> <<exit_code>>" ];

#endif
