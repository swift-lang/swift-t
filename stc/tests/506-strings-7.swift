
import assert;

(int r) iid (int x) {
    r = x;
}

(float r) fid (float x) {
    r = x;
}

(string r) sid (string x) {
    r = x;
}


main {
    assertEqual(1234, toint("1234"), "toint");
    assertEqual(-3.142, tofloat("-3.142"), "tofloat");
    assert("3.142" == fromfloat(3.142), "fromfloat");
    assert("4321" == fromint(4321), "fromint");

    assertEqual(1234, toint(sid("1234")), "toint");
    assertEqual(-3.142, tofloat(sid("-3.142")), "tofloat");
    assert("3.142" == fromfloat(fid(3.142)), "fromfloat");
    assert("4321" == fromint(iid(4321)), "fromint");

    string a = sid("1234");
    string b = sid("-3.142");
    float c = fid(3.142);
    int d = iid(4321);

    wait(a,b,c,d) {
        assertEqual(1234, toint(a), "toint");
        assertEqual(-3.142, tofloat(b), "tofloat");
        assert("3.142" == fromfloat(c), "fromfloat");
        assert("4321" == fromint(d), "fromint");
    }
}
