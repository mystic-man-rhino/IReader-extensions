listOf("en").map { lang ->
  Extension(
    name = "Neosekaitranslations",
    versionCode = 1,
    libVersion = "1",
    lang = lang,
    description = "",
    nsfw = false,
    type = ExtensionType.Madara
  )
}.also(::register)