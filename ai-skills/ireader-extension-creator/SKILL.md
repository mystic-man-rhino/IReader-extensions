---
name: ireader-extension-creator
description: Create new IReader novel/manga source extensions
---

# IReader Extension Creator

Create source extensions for IReader (novel/manga reader app).

## When to Use

- Creating a new novel/manga source extension
- Adding a source to an existing multisrc template
- Registering an extension in build.gradle.kts

## Quick Start

```
1. Test website with curl (NEVER skip this)
2. Extract CSS selectors from actual HTML
3. Choose: Madara → @MadaraSource, else → SourceFactory
4. Write source using templates from references/templates.md
5. Add KSP annotations
6. Build: ./gradlew :extensions:individual:{lang}:{name}:assemble{Lang}Debug
7. Run tests: ./gradlew :extensions:individual:{lang}:{name}:testEnDebugUnitTest
```

## Decision Tree

```
Is the site a Madara/WordPress theme?
│
├── YES → Use @MadaraSource (ZERO CODE!)
│         Signs:
│         - URL pattern: /novel/novel-name/chapter-1/
│         - Has /wp-admin/ page
│
└── NO → Use SourceFactory with KSP annotations
```

## Rules

### MUST Follow

1. **Test website first** - Use `curl -sL "URL"` to verify site works
2. **Use @AutoSourceId** - Never hardcode source ID
3. **Use @GenerateTests** - All sources need test annotations
4. **Package matches folder** - `ireader.{name}` in `ireader/{name}/`
5. **Class is abstract** - Must extend `SourceFactory`
6. **Has @Extension** - Required annotation
7. **Run tests** - Verify with `./gradlew testEnDebugUnitTest`

### NEVER Do

1. **Never skip testing** - Always test with curl first
2. **Never use @Serializable** - Use `kotlinx.serialization.json.*` (dynamic)
3. **Never use jsoup** - Use `com.fleeksoft.ksoup` instead
4. **Never use OkHttp** - Use `io.ktor` for HTTP
5. **Never hardcode selectors** - Validate against actual HTML
6. **Never run clean** - `assemble` is enough to regenerate KSP tests

### Build & Test

- **assemble** regenerates KSP-generated code (no need for `clean`)
- **testEnDebugUnitTest** runs both unit AND integration tests
- KSP generates integration tests automatically when `integrationTests = true`
- KSP generates unit tests automatically when `unitTests = true`
- Tests use real network requests - ensure internet is available

## File Structure

```
sources/{lang}/{name}/
├── build.gradle.kts
└── main/src/ireader/{name}/
    └── {Name}.kt
```

## build.gradle.kts Template

```kotlin
listOf("{lang}").map { lang ->
  Extension(
    name = "{Name}",
    versionCode = 1,
    libVersion = "2",
    lang = lang,
    description = "Read novels from {Name}",
    nsfw = false,
    icon = DEFAULT_ICON,
  )
}.also(::register)
```

## Source Template

```kotlin
package ireader.{name}

import io.ktor.client.request.get
import ireader.core.source.Dependencies
import ireader.core.source.SourceFactory
import ireader.core.source.asJsoup
import ireader.core.source.findInstance
import ireader.core.source.model.*
import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Document
import kotlinx.serialization.json.*
import tachiyomix.annotations.*

@Extension
@AutoSourceId(seed = "{Name}")
@GenerateFilters(title = true)
@GenerateCommands(detailFetch = true, contentFetch = true, chapterFetch = true)
@GenerateTests(unitTests = true, integrationTests = true, searchQuery = "test", minSearchResults = 1)
@TestFixture(
    "https://example.com/novel/test",
    chapterUrl = "https://example.com/novel/test/chapter-1",
    expectedAuthor = "Author",
    expectedTitle = "Test Novel"
)
@TestExpectations()
@SourceMeta(description = "Read novels from {Name}", nsfw = false)
abstract class {Name}(deps: Dependencies) : SourceFactory(deps = deps) {
    override val lang: String get() = "en"
    override val baseUrl: String get() = "https://example.com"
    override val id: Long get() = {Name}SourceId.ID
    override val name: String get() = "{Name}"

    override val exploreFetchers: List<BaseExploreFetcher> get() = listOf(...)
    override val detailFetcher: Detail get() = SourceFactory.Detail(...)
    override val chapterFetcher: Chapters get() = SourceFactory.Chapters(...)
    override val contentFetcher: Content get() = SourceFactory.Content(...)
}
```

## References

| Document | Description |
|----------|-------------|
| `references/templates.md` | Complete source templates |
| `references/ksp-annotations.md` | All KSP annotations |
| `references/testing-guide.md` | Testing workflow |
| `references/patterns.md` | Common patterns |

## Build Command

```bash
# Build (regenerates KSP code)
./gradlew :extensions:individual:{lang}:{name}:assemble{Lang}Debug

# Run tests
./gradlew :extensions:individual:{lang}:{name}:testEnDebugUnitTest
```
