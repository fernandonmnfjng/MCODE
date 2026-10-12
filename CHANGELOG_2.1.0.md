# MCODE 2.1.0

- Draft/session persistence on close/reopen via `mcode/editor-session.dat`.
- Multiple editor tabs (up to 20), tab switching and per-tab concurrent execution.
- `SetPATH` metadata directive recognized only on physical line 1.
- Saving scripts with `SetPATH` targets that workspace path and confirms overwrites.
- `import path.module` resolves `mcode/path/module.mcode`; module files may also declare `SetPATH` on line 1.
- Workspace-safe file helpers: `read_text`, `write_text`, `file_exists`, `make_dirs`, `list_files`.

Build validation could not be completed in this environment because `services.gradle.org` is unreachable. Test with JDK 21 and run `./gradlew clean build` before installing the generated JAR.
