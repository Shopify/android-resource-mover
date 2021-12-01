# Resource Mover

ResourceMover is a command line tool that helps bulk manage resources in an Android project.

## Installation

1) Clone project
2) Build CLI jar using `./gradlew publishBinary`. This will build the jar into `./libs` in this project's directory.
3) Copy the `resource-mover.gradle` script into your projects root directory
4) Apply the script in your root `build.gradle` using `apply from: "resource-mover.gradle"`

## Usage

### Moving Resources

To move resources from one module in your android project to another run the following command. Note this will only move resources that are only referenced by the "toModule". Resources referenced by other modules will remain untouched:

```
./gradlew moveResources -PfromModule="Example-From-Module-Name" -PtoModule="Example-To-Module-Name"`
```

### Deleting used Resources

To remove resources from a module that are not referenced anywhere in code run:

```
./gradlew removeResources -PfromModule="Example-Module-Name"
```
