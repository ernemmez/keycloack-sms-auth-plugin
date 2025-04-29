# SMS OTP Authentication Plugin for Keycloak

This repository contains a Keycloak authentication plugin designed for the SMS OTP (One Time Password) authentication flow in my own project. This plugin enables users to perform two-factor authentication (2FA) through SMS verification.

## Features

- SMS OTP code delivery
- Phone number verification and registration
- Customizable SMS API integration
- Multi-language support
- reCAPTCHA integration
- Phone number standardization in E164 format
- Code resend functionality
- Session duration management

## Overview

The plugin can send SMS messages via HTTP API and is compatible with various SMS providers. Users can complete SMS verification during initial registration or login, enhancing account security through two-factor authentication.

## Technical Capabilities

- Custom SMS templates
- Multiple authentication flows
- Phone number validation
- Error handling and retry mechanisms
- Session management
- User attribute management
- Configurable OTP length and validity period
- Simulation mode for testing
- Detailed logging and monitoring

## Security

This authentication plugin is specifically tailored for financial applications where security and user verification are crucial requirements.

## Building

1. Clone this repository
1. Install Apache Maven
1. Change into the cloned directory and run
   ```shell
   mvn clean install
   ```
   A file `target/finance.finance-authenticator-v26.0.6.jar` should be created.

If building fails and the problem is caused or related to the dev module or tests, try to run `mvn clean install -DskipTests`.

## Run The Demo
To run the demo, start Docker Compose with the following command:
```shell
docker compose up
```
