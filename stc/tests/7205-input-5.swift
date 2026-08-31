// THIS-TEST-SHOULD-NOT-RUN
// The input() check must survive optimization even when the file is never
// read.  input() promises that its output has the filename of its input, so
// a result that only reaches filename() folds back to the argument string;
// were input() pure, the call and its check would then be dead code and the
// missing file would go unreported.

arguments(input data);

trace("unreachable " + filename(data));
