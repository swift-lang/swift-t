
// Route: no output array, branches that do not use the key,
// and patterns containing spaces and brackets

string A[string];
A["a b"] = "1";
A["c[d]"] = "2";
A["e"] = "3";

route (string t, A) {
  case "e":
    trace("case e");
  pattern "^a b$":
    trace("space:", t);
  pattern "\\[":
    trace("bracket:", t);
  match "*\\[d\\]":
    trace("glob:", t);
  default:
    trace("DEFAULT-SHOULD-NOT-RUN", t);
}
