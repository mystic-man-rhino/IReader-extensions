listOf("en").map { lang ->
  Extension(
    name = "BrightNovels",
    versionCode = 1,
    libVersion = "2",
    lang = lang,
    description = "Read novels from Bright Novels",
    nsfw = false,
    icon = DEFAULT_ICON,
  )
}.also(::register)
