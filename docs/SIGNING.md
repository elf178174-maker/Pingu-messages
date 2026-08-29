# Signing

## You do not need a keystore

The release build is configured so that **no key material is required to produce an installable
APK**. When no keystore is configured, `assembleRelease` falls back to the standard Android debug
key, which the Android Gradle Plugin generates automatically. The resulting APK installs on any
device and is exactly what the CI workflow uploads.

That is the right default for a project people download from Actions artifacts. It is *not*
suitable for the Play Store, and the release notes say so when no key is configured.

## Producing a Play-Store-ready build

You need an upload key. Create one once and keep it safe — losing it means you can never update the
app on Play under the same identity.

```bash
keytool -genkeypair -v \
  -keystore release.jks \
  -keyalg RSA -keysize 4096 -validity 10000 \
  -alias pingu-upload
```

### Locally

Create `keystore.properties` in the project root (it is in `.gitignore`, and it must stay that
way):

```properties
storeFile=/absolute/path/to/release.jks
storePassword=…
keyAlias=pingu-upload
keyPassword=…
```

Then `./gradlew assembleRelease`.

### On GitHub Actions

Add four repository secrets. The release workflow picks them up automatically; with none of them
set it silently uses the debug key instead, so the workflow works either way.

| Secret | Value |
| --- | --- |
| `PINGU_KEYSTORE_BASE64` | `base64 -w0 release.jks` |
| `PINGU_KEYSTORE_PASSWORD` | The keystore password |
| `PINGU_KEY_ALIAS` | `pingu-upload` |
| `PINGU_KEY_PASSWORD` | The key password |

Push a tag to build and publish:

```bash
git tag v1.0.0
git push origin v1.0.0
```

## What is deliberately not done

- **No keystore is committed to the repository**, and `*.jks`, `*.keystore` and
  `keystore.properties` are in `.gitignore`. A signing key in version control is a signing key
  everybody has.
- **No passwords in `build.gradle.kts`.** The build reads them from `keystore.properties` or from
  environment variables, and neither is checked in.
- **The debug fallback is explicit, not accidental.** `app/build.gradle.kts` says in a comment
  exactly when it applies, so nobody ships to Play thinking they signed a release build.
