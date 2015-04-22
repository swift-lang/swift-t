// THIS-TEST-SHOULD-NOT-COMPILE

main {

    // Check that we handle bad escape code gracefully
    string x =
    """
        \x""";
}
