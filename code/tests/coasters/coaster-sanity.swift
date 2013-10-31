import string;
import files;

app (file out) hello (){
    "/bin/echo" @stdout=out;
}

app (file out) hello_coaster (){
    "coaster/bin/echo" @stdout=out;
}

/**
 * Tests only the basic creation of a coaster worker
 */
main()
{
    file output = hello();
    file output2 = hello_coaster();
}
