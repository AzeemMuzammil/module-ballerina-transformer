import ballerina/transformer as _;

public isolated function helloWorld(string firstName, Annot annot) returns string => firstName;

function helloWorld1(table<map<int>> lastName) => ();

public isolated function helloWorld2(string... names) returns string => "Hello World";

function helloWorld3(string firstName, string lastName = "Root") returns string => "Hello World";

type Annot record {
    string val;
};
