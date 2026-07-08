# Changelog

## 0.1.3

- Fixed Marketplace verification by omitting the empty `until-build` plugin
  compatibility attribute from generated plugin descriptors.
- Declared the bundled Python plugin dependency directly and moved the tool
  window factory to Java to avoid Kotlin-generated verifier noise.

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
