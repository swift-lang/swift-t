
// SKIP-THIS-TEST
// this should work but we do not assume everyone has Swift/T/JVM installed

import groovy;

s = groovy("\"HOWDY1\"", "println \"HOWDY2\"");
trace(s);
