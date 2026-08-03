package com.ironlog.app.data.seed

import com.ironlog.app.data.local.entity.ExerciseEntity

object ExerciseSeedData {

    fun getAll(): List<ExerciseEntity> = listOf(
        // === BRUST ===
        ex("Bankdrücken (Langhantel)", "BRUST", "TRIZEPS,SCHULTERN", "LANGHANTEL"),
        ex("Bankdrücken (Kurzhantel)", "BRUST", "TRIZEPS,SCHULTERN", "KURZHANTEL"),
        ex("Schrägbankdrücken (Langhantel)", "BRUST", "TRIZEPS,SCHULTERN", "LANGHANTEL"),
        ex("Schrägbankdrücken (Kurzhantel)", "BRUST", "TRIZEPS,SCHULTERN", "KURZHANTEL"),
        ex("Negativbankdrücken (Langhantel)", "BRUST", "TRIZEPS", "LANGHANTEL"),
        ex("Fliegende (Kurzhantel)", "BRUST", "", "KURZHANTEL"),
        ex("Cable Fly", "BRUST", "", "KABEL"),
        ex("Cable Fly obere Brust", "BRUST", "SCHULTERN", "KABEL"),
        ex("Butterfly Maschine", "BRUST", "", "MASCHINE"),
        ex("Brustpresse (Maschine)", "BRUST", "TRIZEPS,SCHULTERN", "MASCHINE"),
        ex("Liegestütze", "BRUST", "TRIZEPS,SCHULTERN,CORE", "EIGENGEWICHT"),
        ex("Dips (Brust)", "BRUST", "TRIZEPS,SCHULTERN", "EIGENGEWICHT"),

        // === RÜCKEN ===
        ex("Langhantelrudern", "RUECKEN", "BIZEPS", "LANGHANTEL"),
        ex("Kurzhantelrudern", "RUECKEN", "BIZEPS", "KURZHANTEL"),
        ex("Klimmzug", "RUECKEN", "BIZEPS", "EIGENGEWICHT"),
        ex("Klimmzug (Untergriff)", "RUECKEN", "BIZEPS", "EIGENGEWICHT"),
        ex("Latzug breit", "RUECKEN", "BIZEPS", "KABEL"),
        ex("Latzug eng", "RUECKEN", "BIZEPS", "KABEL"),
        ex("Kabelrudern sitzend", "RUECKEN", "BIZEPS", "KABEL"),
        ex("T-Bar Rudern", "RUECKEN", "BIZEPS", "LANGHANTEL"),
        ex("Rudermaschine", "RUECKEN", "BIZEPS", "MASCHINE"),
        ex("Kreuzheben", "RUECKEN", "BEINE,GESAESS,CORE", "LANGHANTEL"),
        ex("Hyperextension", "RUECKEN", "GESAESS", "EIGENGEWICHT"),
        ex("Face Pulls", "RUECKEN", "SCHULTERN", "KABEL"),
        ex("Einarmiges Kabelziehen", "RUECKEN", "BIZEPS", "KABEL"),

        // === BEINE ===
        ex("Kniebeuge (Langhantel)", "BEINE", "GESAESS,CORE", "LANGHANTEL"),
        ex("Frontkniebeuge", "BEINE", "CORE,GESAESS", "LANGHANTEL"),
        ex("Rumänisches Kreuzheben", "BEINE", "GESAESS,RUECKEN", "LANGHANTEL"),
        ex("Rumänisches Kreuzheben (Kurzhantel)", "BEINE", "GESAESS,RUECKEN", "KURZHANTEL"),
        ex("Beinpresse", "BEINE", "GESAESS", "MASCHINE"),
        ex("Hackenschmidt", "BEINE", "GESAESS", "MASCHINE"),
        ex("Ausfallschritte (Langhantel)", "BEINE", "GESAESS", "LANGHANTEL"),
        ex("Ausfallschritte (Kurzhantel)", "BEINE", "GESAESS", "KURZHANTEL"),
        ex("Bulgarische Kniebeuge", "BEINE", "GESAESS", "KURZHANTEL"),
        ex("Beinstrecker", "BEINE", "", "MASCHINE"),
        ex("Beinbeuger liegend", "BEINE", "", "MASCHINE"),
        ex("Beinbeuger sitzend", "BEINE", "", "MASCHINE"),
        ex("Goblet Squat", "BEINE", "GESAESS,CORE", "KURZHANTEL"),
        ex("Sumo Kreuzheben", "BEINE", "GESAESS,RUECKEN", "LANGHANTEL"),

        // === GESÄSS ===
        ex("Hip Thrust (Langhantel)", "GESAESS", "BEINE", "LANGHANTEL"),
        ex("Hip Thrust (Maschine)", "GESAESS", "BEINE", "MASCHINE"),
        ex("Glute Kickback (Kabel)", "GESAESS", "", "KABEL"),
        ex("Abduktion (Maschine)", "GESAESS", "", "MASCHINE"),

        // === SCHULTERN ===
        ex("Schulterdrücken (Langhantel)", "SCHULTERN", "TRIZEPS", "LANGHANTEL"),
        ex("Schulterdrücken (Kurzhantel)", "SCHULTERN", "TRIZEPS", "KURZHANTEL"),
        ex("Schulterdrücken (Maschine)", "SCHULTERN", "TRIZEPS", "MASCHINE"),
        ex("Seitheben (Kurzhantel)", "SCHULTERN", "", "KURZHANTEL"),
        ex("Seitheben (Kabel)", "SCHULTERN", "", "KABEL"),
        ex("Frontheben", "SCHULTERN", "BRUST", "KURZHANTEL"),
        ex("Aufrechtes Rudern", "SCHULTERN", "BIZEPS", "LANGHANTEL"),
        ex("Arnold Press", "SCHULTERN", "TRIZEPS", "KURZHANTEL"),
        ex("Reverse Fly (Kurzhantel)", "SCHULTERN", "RUECKEN", "KURZHANTEL"),
        ex("Reverse Fly (Maschine)", "SCHULTERN", "RUECKEN", "MASCHINE"),

        // === BIZEPS ===
        ex("Langhantelcurls", "BIZEPS", "UNTERARME", "LANGHANTEL"),
        ex("SZ-Curls", "BIZEPS", "UNTERARME", "LANGHANTEL"),
        ex("Kurzhantelcurls", "BIZEPS", "UNTERARME", "KURZHANTEL"),
        ex("Hammercurls", "BIZEPS", "UNTERARME", "KURZHANTEL"),
        ex("Konzentrationscurls", "BIZEPS", "", "KURZHANTEL"),
        ex("Kabelcurls", "BIZEPS", "UNTERARME", "KABEL"),
        ex("Schrägbankcurls", "BIZEPS", "", "KURZHANTEL"),

        // === TRIZEPS ===
        ex("Trizepsdrücken am Kabel", "TRIZEPS", "", "KABEL"),
        ex("Trizepsdrücken Seil", "TRIZEPS", "", "KABEL"),
        ex("French Press (SZ-Stange)", "TRIZEPS", "", "LANGHANTEL"),
        ex("Stirndrücken (Kurzhantel)", "TRIZEPS", "", "KURZHANTEL"),
        ex("Dips (Trizeps)", "TRIZEPS", "BRUST,SCHULTERN", "EIGENGEWICHT"),
        ex("Kickbacks (Kurzhantel)", "TRIZEPS", "", "KURZHANTEL"),
        ex("Enges Bankdrücken", "TRIZEPS", "BRUST,SCHULTERN", "LANGHANTEL"),

        // === CORE ===
        ex("Plank", "CORE", "", "EIGENGEWICHT"),
        ex("Cable Crunch", "CORE", "", "KABEL"),
        ex("Beinheben hängend", "CORE", "", "EIGENGEWICHT"),
        ex("Beinheben liegend", "CORE", "", "EIGENGEWICHT"),
        ex("Russian Twist", "CORE", "", "EIGENGEWICHT"),
        ex("Ab-Roller", "CORE", "", "EIGENGEWICHT"),
        ex("Seitliches Plank", "CORE", "", "EIGENGEWICHT"),

        // === UNTERARME ===
        ex("Unterarmcurls", "UNTERARME", "", "LANGHANTEL"),
        ex("Reverse Curls", "UNTERARME", "BIZEPS", "LANGHANTEL"),

        // === WADEN ===
        ex("Wadenheben stehend (Maschine)", "WADEN", "", "MASCHINE"),
        ex("Wadenheben sitzend", "WADEN", "", "MASCHINE"),
        ex("Wadenheben an der Beinpresse", "WADEN", "", "MASCHINE"),
    )

    private fun ex(
        name: String,
        primary: String,
        secondary: String,
        category: String
    ) = ExerciseEntity(
        name = name,
        primaryMuscleGroup = primary,
        secondaryMuscleGroups = secondary,
        category = category,
        isCustom = false
    )
}
