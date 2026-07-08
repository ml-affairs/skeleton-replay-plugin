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
- Follow in IDE sync from the report timeline to PyCharm source, Project view,
  and function/line highlighting.
- Marketplace-ready Gradle setup with signing and beta publishing hooks.

## Product Surface

<table>
  <tr>
    <td width="50%" valign="top">
      <img src="docs/product/replay.svg" alt="" width="40"><br>
      <strong>Embedded replay</strong><br>
      Load <code>report.html</code> inside PyCharm and step through the Skeleton timeline without leaving the IDE.
    </td>
    <td width="50%" valign="top">
      <img src="docs/product/architecture-graph.svg" alt="" width="40"><br>
      <strong>Follow in IDE</strong><br>
      Keep the Project view, editor, and active function highlight aligned with the selected runtime event.
    </td>
  </tr>
  <tr>
    <td width="50%" valign="top">
      <img src="docs/product/workflow.svg" alt="" width="40"><br>
      <strong>Workflow evidence</strong><br>
      Read the generated <code>workflow.md</code> beside the report, artifacts, quality summary, and run log.
    </td>
    <td width="50%" valign="top">
      <img src="docs/product/trace.svg" alt="" width="40"><br>
      <strong>Script and pytest replay</strong><br>
      Run Skeleton against Python files or pytest targets using the project interpreter configured in PyCharm.
    </td>
  </tr>
</table>

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
