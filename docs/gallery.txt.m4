
////

Swift/T Gallery, asciidoc format
http://swift-lang.org/Swift-T/gallery.html

////

:toc:
:numbered:

////
Settings:
////
:miscellaneous.newline: \n

= Swift/T Gallery

This page demonstrates the ease of use of Swift/T when constructing
common application patterns.

Links:

* link:swift.html[General documentation for Swift/T]

To report problems with these examples, post to
http://lists.mcs.anl.gov/mailman/listinfo/exm-user[the ExM user list].

== Hello world

example(hello-world/hello-world.swift)

== Running shell commands

This script converts itself to octal in +mtc.octal+.

example(mtc/mtc1.swift)

This script splits itself into lines, where line _i_ is in file +out-+
_i_ +.txt+

example(mtc/mtc2.swift)

Note that each +/bin/echo+ is eligible to run concurrently.  See
link:guide.html#_invocation[Invocation] for how to run with many
processes.

== Reductions

A simplified version of the MapReduce model is to just compute many
things and assemble them together at the end.

This script splits itself into lines, then reassembles the original
script.

example(mtc/mtc3.swift)

Note that leading whitespace is trimmed by +file_lines()+, and +cat()+
is part of the Swift/T standard library in module +unix+.

== Python and Numpy

See this section for information about calling Python or Numpy:
link:guide.html#_external_scripting_support[Swift/T Guide: Python]

////
Local Variables:
mode: doc
eval: (auto-fill-mode 1)
End:
////
