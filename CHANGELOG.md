# Changelog

## 0.1.6

- Fixed a Tool Window initialization crash caused by unsupported CSS in Swing
  HTML rendering for the Workflow, Artifacts, and Quality tabs.
- Removed hard Python PSI linkage from Follow in IDE highlighting.

## 0.1.5

- Fixed artifact loading for `session.json`, `report.html`, sibling artifact
  files, artifact directories, and `file://` URI drops.
- Added a Project view `Load in Skeleton` action for deterministic loading
  without drag/drop.
- Simplified the workbench toolbar to one artifact loader and the Follow in IDE
  toggle.
- Removed the separate Disengage control; turning off Follow in IDE clears
  active Skeleton highlighting.

## 0.1.4

- Restored standalone `report.html` loading when no sibling `session.json` is
  available.
- Added a direct path loader for hidden `.skeleton` artifact paths.
- Opened the artifact chooser at the project `.skeleton` directory when it
  exists.

## 0.1.3

- Fixed Marketplace verification by omitting the empty `until-build` plugin
  compatibility attribute from generated plugin descriptors.
- Declared the bundled Python plugin dependency directly and moved the tool
  window factory to Java to avoid Kotlin-generated verifier noise.
- Reduced Follow in IDE churn by navigating only when the active source context
  changes.
- Added a Disengage control to disable follow mode and clear Skeleton
  highlights.
- Replaced the native session chooser with a project-root IntelliJ chooser.
- Rendered Workflow, Artifacts, and Quality tabs as richer HTML views with
  highlighted observations and clickable artifact links.

## 0.1.2

- Added Follow in IDE sync from the embedded Skeleton report timeline to
  PyCharm source navigation, Project view selection, and function/line
  highlighting.
- Added product artwork to the README feature surface.

## 0.1.1

- Updated the bundled plugin icon artwork.
- Added session loading, artifact drag/drop, report rendering, and pytest replay actions.

## 0.1.0

- Initial Marketplace-ready PyCharm plugin scaffold.
- Added Skeleton Tool Window placeholders for report, workflow, artifacts,
  quality, and run log.
- Added project settings for the Python engine and default run options.
