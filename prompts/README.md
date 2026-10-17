# Prompt assets

No prompt is invoked by the workflow runtime. If optional local-model drafting is added, store each prompt here with its intended mode, input data classes, expected output shape and review requirement. Use the `draft`, `migrate` and `review` modes defined in `artifacts/design/architecture/agent-modes.md`.

Do not place credentials, unmasked production data, or unrestricted Pega exports in prompt fixtures. Prompt changes are reviewed like source code. Outputs are suggestions and never deploy without the same validation and human approval as manually authored CWM.
