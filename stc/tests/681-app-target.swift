// Test app location dispatch

import assert;
import files;
import io;
import string;
import location;

app (file o) hostname() {
  "hostname" @stdout=o;
}

(string o) extract_hostname(file f) {
  o = trim(read(f));
}

main {
  foreach i in [1:10] {
    string host1 = extract_hostname(hostname());
    // Run on same host
    string host2 = extract_hostname(@location=hostmap_one_worker(host1) hostname());
    assertEqual(host1, host2, sprintf("Check hostnames same trial %i", i));
  }
}
