# AnaL&oacute;g

AnaLog (pronounced with stressed "*o*") is a tool for convenient real-time displaying of various logs in your browser.  
Think of it as of web interface to traditional [tail](https://en.wikipedia.org/wiki/Tail_(Unix)) utility
with a bunch of improvements:
* log level syntax highlighting
* predefined and on-demand log choice configuration (via YAML or URL correspondingly)
* fetching logs from various sources including remote servers, Docker containers and Kubernetes resources
* ability to display several separate log sources as a single composite log
* automatic formatting and highlighting of XML documents in logs
* downloading currently chosen log fully or partly (for file logs only)

## Project Status
AnaLog is personal R&D project and currently it is still in development
and stabilization stage. As a consequence:
1. It is not production-ready yet product but is being prepared to be.
2. Its web UI interface aimed to current testing users audience and
therefore supports their native language only (Russian).

If you have any questions about the product, want to try it or to help
in its development, please feel free to [contact the author](mailto:toparvion@gmx.com) or
[submit an issue](https://github.com/Toparvion/analog/issues/new).

## What it looks like?
### For end users
Click the images to see them in full size.

<a href="https://raw.githubusercontent.com/wiki/Toparvion/analog/images/composite-example.png">
<img src="https://raw.githubusercontent.com/wiki/Toparvion/analog/images/composite-example.png" height="170">
</a>&nbsp;
<a href="https://raw.githubusercontent.com/wiki/Toparvion/analog/images/choices-example.png">
<img src="https://raw.githubusercontent.com/wiki/Toparvion/analog/images/choices-example.png" height="170">
</a>&nbsp;
<a href="https://raw.githubusercontent.com/wiki/Toparvion/analog/images/dialogs-example.png">
<img src="https://raw.githubusercontent.com/wiki/Toparvion/analog/images/dialogs-example.png" height="170">
</a>

### For administrator
In case of AnaLog the administrator is a person who installs and
configures AnaLog instances.  
From the administrator's perspective AnaLog:
* is standalone Java application with built-in web server (based on [Spring Boot](https://spring.io/projects/spring-boot) framework)
* works on [Java 11](http://jdk.java.net/11/) and above
* has flexible configuration in 2 YAML files: for system settings and log choices (see [examples](https://github.com/Toparvion/analog/wiki))
* must be installed on every server where the file logs must be fetched from
* relies on `tail`, `docker` and `kubectl` binaries to fetch logs from corresponding sources.

## Installation & Usage
1. Download `analog.zip` from [latest release](https://github.com/Toparvion/analog/releases/latest) page
2. Unpack it and give execution permission to `bin/analog` script (in case of *nix OS)
3. *[optional]* Configure `config/application.yaml` and `config/choices.yaml` by [examples](https://github.com/Toparvion/analog/wiki)
4. Run `bin/analog` (*nix OS) or `bin/analog.bat` (Windows)
5. Open browser on configured host:port (by default `http://localhost:8083`) and type desired log path into URI, for example:
```
http://localhost:8083/#/home/me/apps/my-app/events.log
http://localhost:8083/#/node://my-remote-node/home/me/apps/my-app/events.log
http://localhost:8083/#/docker://my-container
http://localhost:8083/#/kubernetes://my-pod-4g5h57-hj4d
http://localhost:8083/#/k8s://deployment/my-deployment
```
By now the last several lines of the log must be rendered in browser and the new records must be added to them as they appear in the source file.

#### :heavy_exclamation_mark: Security caution
AnaLog does not include any file access restrictions and completely relies on the underlying operation system permission model.
Therefore you must run it under carefully restricted user account in order to prevent queries like `http://localhost:8083/#/etc/passwd`.

## Where to get help?
Because AnaLog is still under development it's not provided with neither
comprehensive documentation nor support. Some basic information can be
found on [Wiki pages](https://github.com/Toparvion/analog/wiki).
Nevertheless the author would be glad to help you with any questions
concerning AnaLog usage. You can ask for help
by means of an [issue](https://github.com/Toparvion/analog/issues/new).

## Contributing
See [CONTRIBUTING](https://github.com/Toparvion/analog/blob/master/CONTRIBUTING.md) document.


## License
AnaLog uses MIT license. See [this document](https://github.com/Toparvion/analog/blob/master/LICENSE) for details.
