# Resource Mover

ResourceMover is a command line tool that helps bulk manage resources in an Android project.

## Installation

1) Clone project
2) Build CLI jar using `./gradlew publishBinary`. This will build the jar into `./lib` in this project's directory
3) Copy the `resource-mover.gradle` script into your project's root directory
4) Update the `resourceMoverBinaryPath` in the copied `resource-mover.gradle` to point to the jar built in step 2
5) Apply the script in your root `build.gradle` using `apply from: "resource-mover.gradle"`

## Usage

### Moving Resources

To move resources from one module in your android project to another run the following command. Note this will only move resources that are only referenced by the "toModule". Resources referenced by other modules will remain untouched:

```
./gradlew moveResources -PfromModule="Example-From-Module-Name" -PtoModule="Example-To-Module-Name"`
```

### Deleting Unused Resources

To remove resources from a module that are not referenced anywhere in code run:

```
./gradlew removeResources -PfromModule="Example-Module-Name"
```
