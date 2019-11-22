datalog-json-parser
===================

This project supports the parsing of [Datomic datalog](https://docs.datomic.com/on-prem/query.html) as read from plain JSON data into Clojure data structures.

## Using as a Dependency

For git repo use add your dependency as:

```clojure
{deps
  ... ...
  {org.parkerici/datalog-json-parser
   {:git/url "git@github.com:ParkerICI/datalog-json-parser"
    :sha "2cdf079a7bf20c533ed7267fc55fc5cc4ffa55dc"}}}
```

Substituting the latest stable SHA.

## Rationale

The datalog json parser reads edn as naively parsed into JSON in Clojure (via data.json, Cheshire, etc.) and transforms it into
datalog. It does so by parsing strings that begin with ":" as keywords, "?" a symbols (datalog vars), and so on. It uses
structurally aware query parsing powered by spec and its collection-oriented regexes to resolve function names like `missing`
and components of datalog queries like `or`, `and-join` into symbols as well.

This handles the vast majority of query cases without requiring that a full implementation of a semantically rich serialization library
(like edn or transit) exist for the client language. For our own use at Parker, this was done to support query via R. It also
allows query literals to be written more succinctly in languages like Python which do not offer as many metaprogramming
affordances (as e.g. Clojure, R, and Julia do).

```
'[:find ?title
  :in $ ?artist-name
  :where
  [?a :artist/name ?artist-name]
  [?t :track/artists ?a]
  [?t :track/name ?title]]
```

Can be written in Python as:

```
[":find" "?title"
 ":in" "$" "?artist-name"
 ":where"
 ["?a" ":artist/name" "?artist-name"]
 ["?t" ":track/artists" "?a"]
 ["?t" ":track/name" "?title"]]
```

And serialized directly to plain JSON, then transformed into an edn query by the parser.
