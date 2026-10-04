# Third-party components

Java generation uses [opensource COBOL 4J](https://github.com/opensourcecobol/opensourcecobol4j),
version 2.1.0, at commit `f46b43d1b50f47802f2d5c833f79202fb053d2ad`.
The compiler is GPLv3 and the Java libcobj runtime is LGPLv3 or later.
The bootstrap script retains the upstream `COPYING` and `COPYING.LIB` files beside
the installed runtime. The corresponding source is fetched at the pinned commit
and remains available in the adjacent dependency checkout. Generated source files
identify the generator. Runtime binaries are fetched/built dependencies.

`tools/dependencies.lock.json` pins the Maven artifact URLs and SHA-256 hashes.
The dependencies are Guava 33.5.0-jre (Apache-2.0), SQLite JDBC 3.51.0.0
(Apache-2.0/BSD), Commons CLI 1.10.0 (Apache-2.0), JSON-java 20250517 (public-domain
project), SLF4J API/simple 2.0.17 (MIT), and PostgreSQL JDBC 42.7.5 (BSD-2-Clause).
Keep their bundled license and notice files with any distribution of the JARs.
For authoritative licensing details, consult each artifact's bundled notices
and upstream project. The dependency JARs are used separately on the classpath.
