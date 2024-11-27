# Finance Keycloak Plugins

This repository contains the source code for a collection of Keycloak MFA plugins. The plugins are:
* [SMS authenticator](sms-authenticator/README.md): Provides SMS as authentication step. SMS are sent via HTTP API, which can be configured. (production ready)

The different plugins are documented in the submodules README. If you need support for deployment or adjustments, please contact [support@verdigado.com](mailto:support@verdigado.com).

## Development
Run the Quarkus distribution in development mode for live reloading and debugging similar to: https://github.com/keycloak/keycloak/tree/main/quarkus#contributing

```shell
mvn -f some_module/pom.xml compile quarkus:dev
```

Works great:)
https://github.com/keycloak/keycloak/discussions/11841

## Building

1. Clone this repository
1. Install Apache Maven
1. Change into the cloned directory and run
   ```shell
   mvn clean install
   ```
   A file `target/netzbegruenung.keycloak-2fa-sms-authenticator.jar` should be created.

If building fails and the problem is caused or related to the dev module or tests, try to run `mvn clean install -DskipTests`.
