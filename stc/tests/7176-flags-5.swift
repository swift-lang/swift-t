// THIS-TEST-SHOULD-NOT-RUN
// A non-boolean flag with no default is required: omitting it is
// reported at run time by argv():
// "argv(): The command line did not provide 'a'"

flags(int a);

trace(a);
