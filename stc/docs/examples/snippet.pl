#!/usr/bin/perl -ns

# Usage: snippet.pl <snippet number> <files...>

BEGIN {

    # The snippet number we are looking for
    $snippet = $n;
    # Are we currently in the desired snippet?
    $in_snippet = 0;

    if (! $snippet) {
        print("No SNIPPET!\n");
        return 1;
    }
}

if ($_ =~ "SNIPPET") {

    if (/.*SNIPPET (.*)/) {
        $number = $1;
        # print("SNIPPET $number\n");
    } else {
        die("Bad snippet line: $_\n");
    }

    if ($number == $snippet ) {
        $in_snippet = 1;
        next;
    }
    if ($number == "END" ) {
        $in_snippet = 0;
        next;
    }
}

if ($in_snippet) {
    print;
}
