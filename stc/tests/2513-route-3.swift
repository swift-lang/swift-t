
// Route inside a function, and nested routes

(string o[string]) classify(string A[string]) {
  route (string t, A, string R[string]) {
    match "a*":
      R[t] = "a";
    default:
      R[t] = "other";
  }
  o = R;
}

main {
  string A[string] = { "apple": "1", "berry": "2" };
  string C[string] = classify(A);
  trace(C["apple"], C["berry"]);

  route (string k, A) {
    case "apple":
      string inner[string] = { "x": "y" };
      route (string k2, inner) {
        case "x":
          trace("nested:", k, k2, inner[k2]);
      }
  }
}
