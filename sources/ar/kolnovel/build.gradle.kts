listOf("ar").map { lang ->
  Extension(
    name = "KolNovel",
    versionCode = 9,
    libVersion = "1",
    lang = lang,
    description = "",
    nsfw = false,
    icon = DEFAULT_ICON
  )
}.also(::register)
