# Protobuf sources (draft)

These `.proto` files are **documentation-first** sketches for the future `:plugin-api` Gradle module.

When moving to code generation, copy them into the module and configure `protobuf` Gradle plugin with a single import root so `import "common.proto";` resolves.
