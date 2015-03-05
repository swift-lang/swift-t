dnl Helper functions to generate Asciidoc
define(`EXAMPLE',`
*File:* +$1+
----
include::gallery/$1[]
----
')
