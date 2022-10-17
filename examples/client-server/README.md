datalog-json-parser
===================

Example minimal server running the datalog json parser

## Building the server as an uberjar

The example server can be built with:

```
make package
```

## Running

Run the example server from the built target:

```
cd target/example-server
chmod a+x example-server
./example-server localhost 8988
```

The query server expects the following environment variables for the Datomic peer server:

```
PS_ENDPOINT (default "dev")
PS_ACCESS_KEY (default "dev")
PS_SECRET (default "localhost:4338")
```

## Testing query server

You can use CURL to test the example server from a terminal:

```
curl -H "Content-Type: application/json" -X POST -d '{"query" : {":find" : [["count","?g"]],  ":in" : ["$"], ":where" : [["?g", ":db/ident"]]}}' localhost:8988/query/db-name
```
