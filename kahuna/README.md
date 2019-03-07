# Kahuna

The media search & management app.

## Build

Assets end up in a .jar file after a Docker sbt build.
The Node generated assets need to be in the working directory before the sbt build in order to be picked up.

```
cd kahuna
./setup.sh
./dist.sh
```
then
```
sbt kahuna/docker:publish
```