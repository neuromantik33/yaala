# yaala
"Yet Another Access Log Analyzer"

Yaala is an HTTP log monitoring console program.

It allows you to `tail` an HTTP log file
created by a web server such as Apache or Nginx and gather some useful
metrics such as total requests-per-second, hits, increase and throughput.

## Installation

### JVM

Simply build the release using gradle, and it will create a distribution
in `./build/install/`

```bash
./gradlew installDist
```

Then you can run it using

```bash
./build/install/yaala/bin/yaala --help
```

### GraalVM native image

If on a Linux distribution (not tested on Windows or OSX), to build a
native release execute the following

```bash
./gradlew clean nativeImage
```

Then you can find the executable and run it using

```bash
./build/graal/yaala --help
```

## Usage

```
yaala [options] <access_log_path>
```

The `path_to_access_log` is a path on the file system pointing to 
the HTTP `access.log` file to tail (default is `/tmp/access.log`).

### cli flags

| option               | default          | purpose                                                                                                                      |
|----------------------|------------------|------------------------------------------------------------------------------------------------------------------------------|
| `--alert-threshold`  | `10 rps`         | The rate of total requests per second at which point an alert will be displayed.                                             |
| `--format`           | `CLF`            | The log format to use (only `CLF` and `INGRESS_NGINX` are supported at the moment).                                          |
| `--ui-refresh`       | `250ms`          | The UI refresh period in milliseconds.                                                                                       |
| `--route-depth`      | `1`              | The depth at which to truncate routes into sections (useful if working with a API gateway and all routes start with `/api`). |
| `--alert-delay`      | `2m`             | The rate of total requests per second at which point an alert will be displayed.                                             |
| `--alert-cooldown`   | `2m`             | The cooldown period in seconds to wait after an alert is triggered to remove the alert in order to avoid thrashing.          |

See `yaala --help` for details

## Possible improvements

- The UI was written hastily, is very procedural, and lacks some flexibility
regarding layout, placement, and perhaps the ability to sort sections by different criteria.

- There is a small bug when tailing and already large `access.log`, all metrics seem to
momentarily spike with absurdly high throughput, and reqs/sec. This is due to the
step size of 10s default and no filtering is done on the log events on their timestamps
in order to keep the total number of hits. I should spend more time to correct this jittery
startup.

- Although no persistence layer exists per-se (an embedded database
would have been overkill), but using [Micrometer] as a registry for all metrics
of interest allows us to potentially plug in push exporters during build
time to ship all gathered metrics to a [Prometheus] or even [Datadog]
collector for more involved querying, retention and aggregation.

- Being a CLI app, it's bad form to create complected multi-threaded applications, but perhaps
using separate threads for alerting and rendering might be interesting.

- Given how I wasn't able to create a native image with [logback], it's somewhat
difficult diagnosing errors, and printing to console is not possible as it is a console UI.
More time spent trying to grok the various build switches for Graal might be 
time well spent.

- For testing, I was unable to procure a web server which wrote logs in CLF format,
  so I included an optional parser to read [nginx-ingress] output which is not the
  default one. I then piped the output using `stern` as follows:

  ```bash
  stern -n nginx-ingress -o raw -e ^I0.* nginx-ingress > /tmp/access.log
  # Set alert delay to 10s and cooldown to 5s for quicker reaction times and max 5 reqs/sec
  ./build/graal/yaala -a 5 --alert-delay 10 --alert-cooldown 5 -f INGRESS_NGINX
  ```

[Micrometer]:http://micrometer.io/
[Prometheus]:http://micrometer.io/docs/registry/prometheus
[Datadog]:http://micrometer.io/docs/registry/datadog
[logback]:http://logback.qos.ch/
[nginx-ingress]:https://kubernetes.github.io/ingress-nginx/


