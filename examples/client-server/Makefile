clean:
	rm -rf target/*

uberjar: clean
	clojure -M:uberjar target/example-server.jar

package: uberjar
	mkdir target/example-server
	mkdir target/example-server/env
	cp target/example-server.jar target/example-server/.
	cp example-server target/example-server/.
	cd target && \
		zip -r example-server.zip example-server