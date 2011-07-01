
proc assert { condition msg } {
    if [ expr ! $condition ] {
        puts msg
        exit 1
    }
}
