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
  string host1 = extract_hostname(hostname());
  int rank = hostmapOneRank(host1);
  location loc = location(rank, HARD, NODE);

  foreach i in [1:500] {
    string host2 = extract_hostname(@location=loc hostname());
    printf("Hostname %i: %s", i, host2);

    /* Check that apps run on the correct host! */
    assertEqual(host2, host1, "hosts");
  }
}

