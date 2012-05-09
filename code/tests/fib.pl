
# Fibonacci function in Perl for correctness testing

my $N = $ARGV[0];

my @F = ();
$F[0] = 0;
$F[1] = 1;
for (my $i = 2; $i < $N ; $i++) {
    $F[$i] = $F[$i-1] + $F[$i-2];
    print "fib($i): " . $F[$i] . "\n";
}
