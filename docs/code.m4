dnl Helper functions to generate Asciidoc
define(`example',`
*File:* +$1+
----
include::gallery/$1[]
----
')
