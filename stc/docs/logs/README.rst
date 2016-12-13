
This is an initial log to DAG processor for Swift/T.

File index
==========

``log2dag``
  Reads a Swift/T log, emits dot file.  Usage: ``log2dag <LOG> <DAG>``

``run``
  Runs the Swift/T program ``PROGRAM.swift``,
  then does ``log2dag`` and runs ``dot``,
  resulting in ``PROGRAM.dot.png``.

``*.swift``
  Initial examples
