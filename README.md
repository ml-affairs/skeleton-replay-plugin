# Skeleton Replay PyCharm Plugin

Embedded PyCharm workbench for [Skeleton](https://github.com/ml-affairs/skeleton).

The plugin is a JetBrains frontend over the `skeleton-replay` Python package. It
does not trace Python code itself. It uses the project's configured Python
interpreter, invokes the installed `skeleton` CLI, reads `session.json`, and
renders the linked artifacts inside PyCharm.

## First Workbench

- Tool Window with Report, Workflow, Artifacts, Quality, and Log tabs.
- Project settings for interpreter command, output directory, include/exclude
  filters, max events, and package installation command.
- Run actions for Python scripts and pytest targets.
- Marketplace-ready Gradle setup with signing and beta publishing hooks.

## Local Development

```bash
gradle runIde
gradle test
gradle buildPlugin
gradle verifyPlugin
```

Marketplace publishing uses environment variables:

```text
CERTIFICATE_CHAIN
PRIVATE_KEY
PRIVATE_KEY_PASSWORD
PUBLISH_TOKEN
```

## Engine Contract

The Python engine writes `session.json` beside each artifact set. The plugin
should treat it as the primary integration contract and avoid parsing CLI output
for artifact discovery.
