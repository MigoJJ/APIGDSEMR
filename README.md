# APIGDSEMRimage

## Setup

Set the API key for the provider you want to use before launching:

```bash
export GEMINI_API_KEY="your-key-here"
export OPENAI_API_KEY="your-key-here"
```

## Health check

Check all configured providers:

```bash
./gradlew healthCheck --no-daemon
```

Check only Gemini:

```bash
./gradlew healthCheck -Pprovider=gemini --no-daemon
```

Check only OpenAI:

```bash
./gradlew healthCheck -Pprovider=openai --no-daemon
```

You can also use the `HEALTHCHECK_PROVIDER` environment variable with `gemini`, `openai`, or `all`.
