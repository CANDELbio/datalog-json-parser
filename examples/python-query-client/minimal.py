import requests
import json

q = {":find": [["count", "?a"]],
     ":where": [["?a", ":artist/name"]]}

req_body = {"query": q,
            "timeout": 3000}
host = "localhost"
port = 8988
endpoint = ("http://%s:%d/query/mbrainz" % (host, port))
print("Querying querl URL:", endpoint)
resp = requests.post(endpoint, json.dumps(req_body))
print(json.loads(resp.content)['query_result'])
