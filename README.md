# Bus-Info
A json REST api built around the open-api provided by [TPER](https://solweb.tper.it/web/tools/open-data/open-data.aspx).

Feel free to use it, the documentation on how to use it can be found here: [DOCS](http://bus-app.fware.net/docs)


## Build native image on local Windows machine

1. You need GraalVM installed with the command available. (check the scala.yml CI "Set up JDK" step for the version)
``` sh
native-image
```
https://www.graalvm.org/downloads/#

2. next you need Visual Studio 2022 version 17.1.0 or later installed on your system.
   *  Desktop development with C++
   *  Windows 10 SDK