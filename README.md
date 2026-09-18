# nJAMS SDK

![GitHub all releases](https://img.shields.io/github/downloads/IntegrationMatters/njams-sdk/total)
![GitHub release (latest by date)](https://img.shields.io/github/v/release/Integrationmatters/njams-sdk)

The nJAMS SDK is a Java library for instrumenting applications so they report process execution and monitoring data
to an nJAMS Server: job and activity execution, process models, and metrics. It is the recommended foundation for
building any nJAMS client — it handles the transport (HTTP, JMS, Kafka), message batching, and the wire format, so
your client code doesn't have to.

The SDK depends on the [Messageformat](https://github.com/IntegrationMatters/njams-messageformat) project, which
defines the shared message structures exchanged between client and server.

## JavaDoc

* [JavaDoc nJAMS SDK](https://integrationmatters.github.io/njams-sdk/index-njams-sdk.html) - The newest SDK JavaDocs
* [JavaDoc nJAMS SDK sample client](https://integrationmatters.github.io/njams-sdk/index-njams-sdk-sample-client.html) -
  The newest sample JavaDocs

## FAQ

* [FAQ](https://github.com/IntegrationMatters/njams-sdk/wiki/FAQ) - Questions on how to use SDK features, including a
  [Getting Started guide](https://github.com/IntegrationMatters/njams-sdk/wiki/FAQ#how-to-get-started) and a summary of
  [breaking changes and deprecations introduced in 6.0](https://github.com/IntegrationMatters/njams-sdk/wiki/FAQ#what-changed-in-60)

## Third Party Libs Licenses

* [Libs](https://github.com/IntegrationMatters/njams-sdk/blob/master/njams-sdk/src/license/THIRD-PARTY.txt) - Licenses
  of third party libs

## License

Copyright (c) 2026 Salesfive Integration Services GmbH

Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
documentation files (the "Software"),
to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge,
publish, distribute, sublicense,
and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the
following conditions:

The above copyright notice and this permission notice shall be included in all copies or substantial portions of the
Software.

The Software shall be used for Good, not Evil.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE
WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR
ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE
OR THE USE OR OTHER DEALINGS
IN THE SOFTWARE.
