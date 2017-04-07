#!/usr/bin/perl

# Put data into buckets for a histogram

# usage: buckets.pl <WIDTH> <FILE>

# Given a file containing numbers, produces
# a plottable histogram file containing buckets
# and counts

# Useful for obtaining job runtime distribution plots

# Example:
# cat file.txt
# 0.1
# 0.2
# 1.1
# buckets.pl 1 file.txt
# 1 2
# 2 1

use POSIX;

# Bucket width:
my $width = $ARGV[0];

my $file = $ARGV[1];

open FILE, "<", $file or die "could not open: $file\n";

my %bucket_counts = ();

while (<FILE>)
{
    # Round up to nearest bucket
    my $index = ceil($_/$width) * $width;

    if (exists $bucket_counts{$index}) {
        $bucket_counts{$index} = $bucket_counts{$index} + 1;
    }
    else {
        $bucket_counts{$index} = 1;
    }
}

@s = sort { $a <=> $b } keys %bucket_counts;

for (@s) {
    printf "%0.3f %8i\n", $_, $bucket_counts{$_};
}

print "\n";

# Local Variables:
# perl-basic-offset: 2
# End:
