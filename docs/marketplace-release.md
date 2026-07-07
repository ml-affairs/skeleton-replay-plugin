# Marketplace Release

Skeleton Replay uses the IntelliJ Platform Gradle Plugin 2.x publishing flow.

## Local gates

```bash
gradle test
gradle buildPlugin
gradle verifyPlugin
```

Install the generated ZIP from disk into a clean PyCharm installation before
publishing.

## Signing and publishing

Set these environment variables outside Git:

```text
CERTIFICATE_CHAIN
PRIVATE_KEY
PRIVATE_KEY_PASSWORD
PUBLISH_TOKEN
```

Publish beta builds first:

```bash
gradle publishPlugin -PpluginChannels=beta
```

Promote stable only after testing the beta build with a real Python project that
has `skeleton-replay` installed in its configured interpreter.
