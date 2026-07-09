# Skeleton Replay

Skeleton Replay embeds Skeleton runtime reports in PyCharm.

It loads Skeleton session artifacts, renders `report.html` inside the IDE, and
keeps workflow, artifact, and quality evidence next to the code. Follow in IDE
can synchronize report timeline events with the Project view and editor so
Python execution can be inspected from the generated runtime report.

## Features

- Load `session.json`, `report.html`, or a Skeleton artifact directory.
- View Report, Workflow, Artifacts, Quality, and Log tabs inside PyCharm.
- Run Skeleton against Python scripts and pytest targets from the editor or
  Project view.
- Follow selected report events in the editor and Project view.
- Use the project's configured Python interpreter and installed Skeleton CLI.
