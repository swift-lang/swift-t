
// SKIP-THIS-TEST
// this should work but we do not assume everyone has Swift/T/JVM installed

import jvm;

s1 = groovy("println \"GROOVY WORKS\"");
trace(s1);

s2 = javascript("print(\"JAVASCRIPT WORKS\");");
trace(s2);

s3 = scala("println(\"SCALA WORKS\")");
trace(s3);

s4 = clojure("\"CLOJURE SETUP\"", "\"CLOJURE WORKS\"");
trace(s4);
