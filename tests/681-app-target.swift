// Test app location dispatch

import assert;
import files;
import io;
import string;
import sys;

app (file o) hostname() {
  "hostname" @stdout=o;
}

(string o) extract_hostname(file f) {
  o = trim(readFile(f));
}

main {
  foreach i in [1:10] {
    string host1 = extract_hostname(hostname());
    // Run on same host
    string host2 = extract_hostname(@location=hostmap_one(host1) hostname());
    assertEqual(host1, host2, sprintf("Check hostnames same trial %i", i));
  }
}
