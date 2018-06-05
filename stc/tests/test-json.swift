
import json;

/*
    print json_type("/home/wozniak/test.json", "main")
    print json_list_length("/home/wozniak/test.json", "main")
    print json_get("/home/wozniak/test.json", "main")
    print json_dict_entries("/home/wozniak/test.json", "repository")
    print json_get("/home/wozniak/test.json", "repository,url")
*/


trace(json_type(input("/home/wozniak/test.json"), "main"));
trace(json_list_length(input("/home/wozniak/test.json"), "main"));
trace(json_get(input("/home/wozniak/test.json"), "main"));
trace(json_dict_entries(input("/home/wozniak/test.json"), "repository"));
trace(json_get(input("/home/wozniak/test.json"), "repository,url"));
trace(json_get(input("/home/wozniak/test.json"), "x"));
