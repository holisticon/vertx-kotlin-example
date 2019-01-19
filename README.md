[![Codacy Badge](https://api.codacy.com/project/badge/Grade/3afe83587b7b41e8a128306b0724149e)](https://app.codacy.com/app/janweinschenker/vertx-kotlin-example?utm_source=github.com&utm_medium=referral&utm_content=holisticon/vertx-kotlin-example&utm_campaign=Badge_Grade_Dashboard)
[![Build Status](https://travis-ci.org/holisticon/vertx-kotlin-example.svg?branch=master)](https://travis-ci.org/holisticon/vertx-kotlin-example)

# A microservice example based on Vert.x, Kotlin and OpenAPI 3 

This is an example for building a microservice with a technology stack consisting of
* kotlin
* vertx with couroutines and RxJava
* OpenApi3
* JDK 11

## Requirements

Requires a JDK 11 or newer due to the usage of the http2 protocol and ALPN.

Furthermore, you need a NASA api key. You can apply for one at [their website](https://api.nasa.gov/index.html#apply-for-an-api-key).

## Before you start

Setup the environment as follows:
1. copy `.env.txt` to `.env`
   ```bash
   > cp .env.txt .env && chmod 755 .env
   ```
1. in `.env`, set the values for `JAVA_HOME` and `NASA_API_KEY`. `JAVA_HOME` must point to your local installation of a JDK 11.
```bash
export JAVA_HOME="/PATH/TO_JDK_v11_HOME"
export NASA_API_KEY="YOUR_NASA_API_KEY"
```

## Building

The `pom.xml` contains a default maven goal that you can use if yu have a 
java executable of version 11 in your path. So all you have to do is

```bash
> mvn 
```

Alternatively, you can configure a path to a JDK 11 installation in the `.env` file. That setting will be used by
the file `test-compile.sh`:

```bash
> ./test-compile.sh 
```

### Running from the IDE

1. Configure a run configuration in your favorite IDE. Configure it to supply the environment 
variables from `.env` to the applycation.
1. Run the `main()` function from the file `main.kt`.

### Running as a far jar

```bash
> mvn package
> java -jar target/application.jar
```

### Running from the CLI


```bash
> ./run.sh
```

## API

Visit one of the local addresses
- [swagger ui instance http 1.1](http://localhost:8081/ui/index.html)
- [swagger ui instance http 2](https://localhost:8443/ui/index.html)

The application exposes a REST API for rating apod images:

You can learn more about an apod

```bash
> curl http://localhost:8080/apod/0
```
```json
{
  "id": "3",
  "dateString": "2018-07-01",
  "title": "Fresh Tiger Stripes on Saturn's Enceladus",
  "imageUriHd": "https://apod.nasa.gov/apod/image/1807/enceladusstripes_cassini_3237.jpg"
}

```

You can get the current rating of an apod:

```bash
> curl http://localhost:8081/apod/0/rating 
```
```json
{
  "id": 0,
  "rating": 8
}
```

Finally you can rate an apod

```bash
> curl -X PUT "https://localhost:8443/apod/0/rating" \
       -H "accept: application/json" \ 
       -H "Content-Type: application/json" \
       -d "{\"rating\":9}"
```

# Further Reading

* A good [introduction](https://medium.com/@elye.project/understanding-suspend-function-of-coroutines-de26b070c5ed) on Kotlin coroutines.
* Official [Kotlin docu](https://vertx.io/docs/vertx-core/kotlin/).
* About Kotlin [web API contracts](https://vertx.io/docs/vertx-web-api-contract/kotlin/).
* About Kotlin [coroutines](https://vertx.io/docs/vertx-lang-kotlin-coroutines/kotlin/).
