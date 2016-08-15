
// SKIP-THIS-TEST
// this should work but we do not assume everyone has Swift/T/JVM installed

import jvm;

s1 = groovy("\"HOWDY1\"", "println \"HOWDY2\"");
trace(s1);

s2 = javascript("\"HOWDY1\"", "print(\"HOWDY2\");");
trace(s2);

s3 = scala("\"HOWDY1\"", "println(\"SCALA HOWDY2\")");
trace(s3);

s4 = clojure("\"HOWDY1\"", "\"CLOJURE HOWDY2\"");
trace(s4);
