import ballerina/transformer as _;

public isolated function hello(string firstName) returns string => firstName;

type Annot record {
    string val;
};
