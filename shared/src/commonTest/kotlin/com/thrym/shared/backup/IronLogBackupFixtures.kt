package com.thrym.shared.backup

/**
 * Golden backups written by the last IronLog code before the Thrym rename (commit 23fdf75,
 * app version 1.4.0, backup schema 15). Both documents were produced once by the unmodified old
 * export code and are frozen: never edit, reformat or regenerate them. They prove that backups
 * users exported from IronLog keep importing into Thrym unchanged.
 *
 * - [ANDROID_EXPORT_JSON]: `BackupRepositoryImpl.exportBackup` of IronLog 1.4.0 (pretty-printed),
 *   105693 bytes, SHA-256 `51aad8e8cbab084d117c74ff54dbf17d2a43a78d22ed7752aaa95b45af1c4005`.
 * - [IOS_EXPORT_JSON]: `SharedStateStore.exportJson` of the IronLog iOS app (compact),
 *   54633 bytes, SHA-256 `e6a002112d9333d27622e74941faff00b2f2a2a6ff2dabeb9d22722f99ada36f`.
 *
 * The data module keeps byte-identical copies in `data/src/test/resources/backup-fixtures/` and
 * checks them against the same hashes.
 *
 * Both documents carry the same graph. It was recorded by driving the old shared store and
 * progression lifecycle like the iOS app does, between 2026-09-01 and 2026-09-11; for the Android
 * document the graph was loaded into the old Room entities and exported by the old repository.
 * Sessions were finished with a per-exercise personal-record rebuild, because the old (and
 * current) `SharedStateStore.finishWorkout` can assign one record id twice when it rebuilds the
 * records of several exercises at once. The graph contains:
 * - the 79 built-in exercises with their German canonical names, two custom exercises
 *   (ids 80, 81; one with Unicode notes) and one archived custom exercise (id 82);
 * - plans "Push A (Kraft)" (LINEAR revision 2 with success threshold 2, DOUBLE, TOTAL_REPS in
 *   IMPERIAL units and RPE_RIR as a superset, MANUAL with individual WARMUP/NORMAL/BACKOFF set
 *   targets), "Pull B" (legacy LINEAR revision 1, MANUAL bodyweight, LINEAR with the unsupported
 *   rule revision 3) and "Beine & Rumpf", plus the meta-plan "Ganzkörper-Rotation" with three
 *   items and one skip;
 * - seven sessions: plan sessions with and without the meta-plan, an explicit deload session, a
 *   session without a recorded deload flag, partial sessions, a free session with multi-line
 *   notes and escaped characters, and one session that was still active at export time;
 * - 49 sets covering NORMAL, WARMUP, DROP_SET, FAILURE and BACKOFF, RPE values, a zero-rep set,
 *   19 workout plan targets and 41 personal records of all four record types;
 * - 14 progression suggestions: PROPOSE_CHANGE, KEEP_TARGET and INSUFFICIENT_DATA outcomes in
 *   the states PENDING, ACCEPTED (edited and unedited), REJECTED, STALE and INFORMATIONAL;
 * - readiness data with three check-ins (complete, partial, edited) and five set intentions.
 */
internal object IronLogBackupFixtures {
    const val ANDROID_EXPORT_SIZE_BYTES: Int = 105693
    const val IOS_EXPORT_SIZE_BYTES: Int = 54633

    val ANDROID_EXPORT_JSON: String = """{
    "formatVersion": 1,
    "schemaVersion": 15,
    "appVersion": "1.4.0",
    "exportedAtEpochMillis": 1791751844960,
    "exercises": [
        {
            "id": 1,
            "name": "Bankdrücken (Langhantel)",
            "primaryMuscleGroup": "BRUST",
            "secondaryMuscleGroups": "TRIZEPS,SCHULTERN",
            "category": "LANGHANTEL",
            "isCustom": false,
            "notes": "",
            "isArchived": false
        },
        {
            "id": 2,
            "name": "Bankdrücken (Kurzhantel)",
            "primaryMuscleGroup": "BRUST",
            "secondaryMuscleGroups": "TRIZEPS,SCHULTERN",
            "category": "KURZHANTEL",
            "isCustom": false,
            "notes": "",
            "isArchived": false
        },
        {
            "id": 3,
            "name": "Schrägbankdrücken (Langhantel)",
            "primaryMuscleGroup": "BRUST",
            "secondaryMuscleGroups": "TRIZEPS,SCHULTERN",
            "category": "LANGHANTEL",
            "isCustom": false,
            "notes": "",
            "isArchived": false
        },
        {
            "id": 4,
            "name": "Schrägbankdrücken (Kurzhantel)",
            "primaryMuscleGroup": "BRUST",
            "secondaryMuscleGroups": "TRIZEPS,SCHULTERN",
            "category": "KURZHANTEL",
            "isCustom": false,
            "notes": "",
            "isArchived": false
        },
        {
            "id": 5,
            "name": "Negativbankdrücken (Langhantel)",
            "primaryMuscleGroup": "BRUST",
            "secondaryMuscleGroups": "TRIZEPS",
            "category": "LANGHANTEL",
            "isCustom": false,
            "notes": "",
            "isArchived": false
        },
        {
            "id": 6,
            "name": "Fliegende (Kurzhantel)",
            "primaryMuscleGroup": "BRUST",
            "secondaryMuscleGroups": "",
            "category": "KURZHANTEL",
            "isCustom": false,
            "notes": "",
            "isArchived": false
        },
        {
            "id": 7,
            "name": "Cable Fly",
            "primaryMuscleGroup": "BRUST",
            "secondaryMuscleGroups": "",
            "category": "KABEL",
            "isCustom": false,
            "notes": "",
            "isArchived": false
        },
        {
            "id": 8,
            "name": "Cable Fly obere Brust",
            "primaryMuscleGroup": "BRUST",
            "secondaryMuscleGroups": "SCHULTERN",
            "category": "KABEL",
            "isCustom": false,
            "notes": "",
            "isArchived": false
        },
        {
            "id": 9,
            "name": "Butterfly Maschine",
            "primaryMuscleGroup": "BRUST",
            "secondaryMuscleGroups": "",
            "category": "MASCHINE",
            "isCustom": false,
            "notes": "",
            "isArchived": false
        },
        {
            "id": 10,
            "name": "Brustpresse (Maschine)",
            "primaryMuscleGroup": "BRUST",
            "secondaryMuscleGroups": "TRIZEPS,SCHULTERN",
            "category": "MASCHINE",
            "isCustom": false,
            "notes": "",
            "isArchived": false
        },
        {
            "id": 11,
            "name": "Liegestütze",
            "primaryMuscleGroup": "BRUST",
            "secondaryMuscleGroups": "TRIZEPS,SCHULTERN,CORE",
            "category": "EIGENGEWICHT",
            "isCustom": false,
            "notes": "",
            "isArchived": false
        },
        {
            "id": 12,
            "name": "Dips (Brust)",
            "primaryMuscleGroup": "BRUST",
            "secondaryMuscleGroups": "TRIZEPS,SCHULTERN",
            "category": "EIGENGEWICHT",
            "isCustom": false,
            "notes": "",
            "isArchived": false
        },
        {
            "id": 13,
            "name": "Langhantelrudern",
            "primaryMuscleGroup": "RUECKEN",
            "secondaryMuscleGroups": "BIZEPS",
            "category": "LANGHANTEL",
            "isCustom": false,
            "notes": "",
            "isArchived": false
        },
        {
            "id": 14,
            "name": "Kurzhantelrudern",
            "primaryMuscleGroup": "RUECKEN",
            "secondaryMuscleGroups": "BIZEPS",
            "category": "KURZHANTEL",
            "isCustom": false,
            "notes": "",
            "isArchived": false
        },
        {
            "id": 15,
            "name": "Klimmzug",
            "primaryMuscleGroup": "RUECKEN",
            "secondaryMuscleGroups": "BIZEPS",
            "category": "EIGENGEWICHT",
            "isCustom": false,
            "notes": "",
            "isArchived": false
        },
        {
            "id": 16,
            "name": "Klimmzug (Untergriff)",
            "primaryMuscleGroup": "RUECKEN",
            "secondaryMuscleGroups": "BIZEPS",
            "category": "EIGENGEWICHT",
            "isCustom": false,
            "notes": "",
            "isArchived": false
        },
        {
            "id": 17,
            "name": "Latzug breit",
            "primaryMuscleGroup": "RUECKEN",
            "secondaryMuscleGroups": "BIZEPS",
            "category": "KABEL",
            "isCustom": false,
            "notes": "",
            "isArchived": false
        },
        {
            "id": 18,
            "name": "Latzug eng",
            "primaryMuscleGroup": "RUECKEN",
            "secondaryMuscleGroups": "BIZEPS",
            "category": "KABEL",
            "isCustom": false,
            "notes": "",
            "isArchived": false
        },
        {
            "id": 19,
            "name": "Kabelrudern sitzend",
            "primaryMuscleGroup": "RUECKEN",
            "secondaryMuscleGroups": "BIZEPS",
            "category": "KABEL",
            "isCustom": false,
            "notes": "",
            "isArchived": false
        },
        {
            "id": 20,
            "name": "T-Bar Rudern",
            "primaryMuscleGroup": "RUECKEN",
            "secondaryMuscleGroups": "BIZEPS",
            "category": "LANGHANTEL",
            "isCustom": false,
            "notes": "",
            "isArchived": false
        },
        {
            "id": 21,
            "name": "Rudermaschine",
            "primaryMuscleGroup": "RUECKEN",
            "secondaryMuscleGroups": "BIZEPS",
            "category": "MASCHINE",
            "isCustom": false,
            "notes": "",
            "isArchived": false
        },
        {
            "id": 22,
            "name": "Kreuzheben",
            "primaryMuscleGroup": "RUECKEN",
            "secondaryMuscleGroups": "BEINE,GESAESS,CORE",
            "category": "LANGHANTEL",
            "isCustom": false,
            "notes": "",
            "isArchived": false
        },
        {
            "id": 23,
            "name": "Hyperextension",
            "primaryMuscleGroup": "RUECKEN",
            "secondaryMuscleGroups": "GESAESS",
            "category": "EIGENGEWICHT",
            "isCustom": false,
            "notes": "",
            "isArchived": false
        },
        {
            "id": 24,
            "name": "Face Pulls",
            "primaryMuscleGroup": "RUECKEN",
            "secondaryMuscleGroups": "SCHULTERN",
            "category": "KABEL",
            "isCustom": false,
            "notes": "",
            "isArchived": false
        },
        {
            "id": 25,
            "name": "Einarmiges Kabelziehen",
            "primaryMuscleGroup": "RUECKEN",
            "secondaryMuscleGroups": "BIZEPS",
            "category": "KABEL",
            "isCustom": false,
            "notes": "",
            "isArchived": false
        },
        {
            "id": 26,
            "name": "Kniebeuge (Langhantel)",
            "primaryMuscleGroup": "BEINE",
            "secondaryMuscleGroups": "GESAESS,CORE",
            "category": "LANGHANTEL",
            "isCustom": false,
            "notes": "",
            "isArchived": false
        },
        {
            "id": 27,
            "name": "Frontkniebeuge",
            "primaryMuscleGroup": "BEINE",
            "secondaryMuscleGroups": "CORE,GESAESS",
            "category": "LANGHANTEL",
            "isCustom": false,
            "notes": "",
            "isArchived": false
        },
        {
            "id": 28,
            "name": "Rumänisches Kreuzheben",
            "primaryMuscleGroup": "BEINE",
            "secondaryMuscleGroups": "GESAESS,RUECKEN",
            "category": "LANGHANTEL",
            "isCustom": false,
            "notes": "",
            "isArchived": false
        },
        {
            "id": 29,
            "name": "Rumänisches Kreuzheben (Kurzhantel)",
            "primaryMuscleGroup": "BEINE",
            "secondaryMuscleGroups": "GESAESS,RUECKEN",
            "category": "KURZHANTEL",
            "isCustom": false,
            "notes": "",
            "isArchived": false
        },
        {
            "id": 30,
            "name": "Beinpresse",
            "primaryMuscleGroup": "BEINE",
            "secondaryMuscleGroups": "GESAESS",
            "category": "MASCHINE",
            "isCustom": false,
            "notes": "",
            "isArchived": false
        },
        {
            "id": 31,
            "name": "Hackenschmidt",
            "primaryMuscleGroup": "BEINE",
            "secondaryMuscleGroups": "GESAESS",
            "category": "MASCHINE",
            "isCustom": false,
            "notes": "",
            "isArchived": false
        },
        {
            "id": 32,
            "name": "Ausfallschritte (Langhantel)",
            "primaryMuscleGroup": "BEINE",
            "secondaryMuscleGroups": "GESAESS",
            "category": "LANGHANTEL",
            "isCustom": false,
            "notes": "",
            "isArchived": false
        },
        {
            "id": 33,
            "name": "Ausfallschritte (Kurzhantel)",
            "primaryMuscleGroup": "BEINE",
            "secondaryMuscleGroups": "GESAESS",
            "category": "KURZHANTEL",
            "isCustom": false,
            "notes": "",
            "isArchived": false
        },
        {
            "id": 34,
            "name": "Bulgarische Kniebeuge",
            "primaryMuscleGroup": "BEINE",
            "secondaryMuscleGroups": "GESAESS",
            "category": "KURZHANTEL",
            "isCustom": false,
            "notes": "",
            "isArchived": false
        },
        {
            "id": 35,
            "name": "Beinstrecker",
            "primaryMuscleGroup": "BEINE",
            "secondaryMuscleGroups": "",
            "category": "MASCHINE",
            "isCustom": false,
            "notes": "",
            "isArchived": false
        },
        {
            "id": 36,
            "name": "Beinbeuger liegend",
            "primaryMuscleGroup": "BEINE",
            "secondaryMuscleGroups": "",
            "category": "MASCHINE",
            "isCustom": false,
            "notes": "",
            "isArchived": false
        },
        {
            "id": 37,
            "name": "Beinbeuger sitzend",
            "primaryMuscleGroup": "BEINE",
            "secondaryMuscleGroups": "",
            "category": "MASCHINE",
            "isCustom": false,
            "notes": "",
            "isArchived": false
        },
        {
            "id": 38,
            "name": "Goblet Squat",
            "primaryMuscleGroup": "BEINE",
            "secondaryMuscleGroups": "GESAESS,CORE",
            "category": "KURZHANTEL",
            "isCustom": false,
            "notes": "",
            "isArchived": false
        },
        {
            "id": 39,
            "name": "Sumo Kreuzheben",
            "primaryMuscleGroup": "BEINE",
            "secondaryMuscleGroups": "GESAESS,RUECKEN",
            "category": "LANGHANTEL",
            "isCustom": false,
            "notes": "",
            "isArchived": false
        },
        {
            "id": 40,
            "name": "Hip Thrust (Langhantel)",
            "primaryMuscleGroup": "GESAESS",
            "secondaryMuscleGroups": "BEINE",
            "category": "LANGHANTEL",
            "isCustom": false,
            "notes": "",
            "isArchived": false
        },
        {
            "id": 41,
            "name": "Hip Thrust (Maschine)",
            "primaryMuscleGroup": "GESAESS",
            "secondaryMuscleGroups": "BEINE",
            "category": "MASCHINE",
            "isCustom": false,
            "notes": "",
            "isArchived": false
        },
        {
            "id": 42,
            "name": "Glute Kickback (Kabel)",
            "primaryMuscleGroup": "GESAESS",
            "secondaryMuscleGroups": "",
            "category": "KABEL",
            "isCustom": false,
            "notes": "",
            "isArchived": false
        },
        {
            "id": 43,
            "name": "Abduktion (Maschine)",
            "primaryMuscleGroup": "GESAESS",
            "secondaryMuscleGroups": "",
            "category": "MASCHINE",
            "isCustom": false,
            "notes": "",
            "isArchived": false
        },
        {
            "id": 44,
            "name": "Schulterdrücken (Langhantel)",
            "primaryMuscleGroup": "SCHULTERN",
            "secondaryMuscleGroups": "TRIZEPS",
            "category": "LANGHANTEL",
            "isCustom": false,
            "notes": "",
            "isArchived": false
        },
        {
            "id": 45,
            "name": "Schulterdrücken (Kurzhantel)",
            "primaryMuscleGroup": "SCHULTERN",
            "secondaryMuscleGroups": "TRIZEPS",
            "category": "KURZHANTEL",
            "isCustom": false,
            "notes": "",
            "isArchived": false
        },
        {
            "id": 46,
            "name": "Schulterdrücken (Maschine)",
            "primaryMuscleGroup": "SCHULTERN",
            "secondaryMuscleGroups": "TRIZEPS",
            "category": "MASCHINE",
            "isCustom": false,
            "notes": "",
            "isArchived": false
        },
        {
            "id": 47,
            "name": "Seitheben (Kurzhantel)",
            "primaryMuscleGroup": "SCHULTERN",
            "secondaryMuscleGroups": "",
            "category": "KURZHANTEL",
            "isCustom": false,
            "notes": "",
            "isArchived": false
        },
        {
            "id": 48,
            "name": "Seitheben (Kabel)",
            "primaryMuscleGroup": "SCHULTERN",
            "secondaryMuscleGroups": "",
            "category": "KABEL",
            "isCustom": false,
            "notes": "",
            "isArchived": false
        },
        {
            "id": 49,
            "name": "Frontheben",
            "primaryMuscleGroup": "SCHULTERN",
            "secondaryMuscleGroups": "BRUST",
            "category": "KURZHANTEL",
            "isCustom": false,
            "notes": "",
            "isArchived": false
        },
        {
            "id": 50,
            "name": "Aufrechtes Rudern",
            "primaryMuscleGroup": "SCHULTERN",
            "secondaryMuscleGroups": "BIZEPS",
            "category": "LANGHANTEL",
            "isCustom": false,
            "notes": "",
            "isArchived": false
        },
        {
            "id": 51,
            "name": "Arnold Press",
            "primaryMuscleGroup": "SCHULTERN",
            "secondaryMuscleGroups": "TRIZEPS",
            "category": "KURZHANTEL",
            "isCustom": false,
            "notes": "",
            "isArchived": false
        },
        {
            "id": 52,
            "name": "Reverse Fly (Kurzhantel)",
            "primaryMuscleGroup": "SCHULTERN",
            "secondaryMuscleGroups": "RUECKEN",
            "category": "KURZHANTEL",
            "isCustom": false,
            "notes": "",
            "isArchived": false
        },
        {
            "id": 53,
            "name": "Reverse Fly (Maschine)",
            "primaryMuscleGroup": "SCHULTERN",
            "secondaryMuscleGroups": "RUECKEN",
            "category": "MASCHINE",
            "isCustom": false,
            "notes": "",
            "isArchived": false
        },
        {
            "id": 54,
            "name": "Langhantelcurls",
            "primaryMuscleGroup": "BIZEPS",
            "secondaryMuscleGroups": "UNTERARME",
            "category": "LANGHANTEL",
            "isCustom": false,
            "notes": "",
            "isArchived": false
        },
        {
            "id": 55,
            "name": "SZ-Curls",
            "primaryMuscleGroup": "BIZEPS",
            "secondaryMuscleGroups": "UNTERARME",
            "category": "LANGHANTEL",
            "isCustom": false,
            "notes": "",
            "isArchived": false
        },
        {
            "id": 56,
            "name": "Kurzhantelcurls",
            "primaryMuscleGroup": "BIZEPS",
            "secondaryMuscleGroups": "UNTERARME",
            "category": "KURZHANTEL",
            "isCustom": false,
            "notes": "",
            "isArchived": false
        },
        {
            "id": 57,
            "name": "Hammercurls",
            "primaryMuscleGroup": "BIZEPS",
            "secondaryMuscleGroups": "UNTERARME",
            "category": "KURZHANTEL",
            "isCustom": false,
            "notes": "",
            "isArchived": false
        },
        {
            "id": 58,
            "name": "Konzentrationscurls",
            "primaryMuscleGroup": "BIZEPS",
            "secondaryMuscleGroups": "",
            "category": "KURZHANTEL",
            "isCustom": false,
            "notes": "",
            "isArchived": false
        },
        {
            "id": 59,
            "name": "Kabelcurls",
            "primaryMuscleGroup": "BIZEPS",
            "secondaryMuscleGroups": "UNTERARME",
            "category": "KABEL",
            "isCustom": false,
            "notes": "",
            "isArchived": false
        },
        {
            "id": 60,
            "name": "Schrägbankcurls",
            "primaryMuscleGroup": "BIZEPS",
            "secondaryMuscleGroups": "",
            "category": "KURZHANTEL",
            "isCustom": false,
            "notes": "",
            "isArchived": false
        },
        {
            "id": 61,
            "name": "Trizepsdrücken am Kabel",
            "primaryMuscleGroup": "TRIZEPS",
            "secondaryMuscleGroups": "",
            "category": "KABEL",
            "isCustom": false,
            "notes": "",
            "isArchived": false
        },
        {
            "id": 62,
            "name": "Trizepsdrücken Seil",
            "primaryMuscleGroup": "TRIZEPS",
            "secondaryMuscleGroups": "",
            "category": "KABEL",
            "isCustom": false,
            "notes": "",
            "isArchived": false
        },
        {
            "id": 63,
            "name": "French Press (SZ-Stange)",
            "primaryMuscleGroup": "TRIZEPS",
            "secondaryMuscleGroups": "",
            "category": "LANGHANTEL",
            "isCustom": false,
            "notes": "",
            "isArchived": false
        },
        {
            "id": 64,
            "name": "Stirndrücken (Kurzhantel)",
            "primaryMuscleGroup": "TRIZEPS",
            "secondaryMuscleGroups": "",
            "category": "KURZHANTEL",
            "isCustom": false,
            "notes": "",
            "isArchived": false
        },
        {
            "id": 65,
            "name": "Dips (Trizeps)",
            "primaryMuscleGroup": "TRIZEPS",
            "secondaryMuscleGroups": "BRUST,SCHULTERN",
            "category": "EIGENGEWICHT",
            "isCustom": false,
            "notes": "",
            "isArchived": false
        },
        {
            "id": 66,
            "name": "Kickbacks (Kurzhantel)",
            "primaryMuscleGroup": "TRIZEPS",
            "secondaryMuscleGroups": "",
            "category": "KURZHANTEL",
            "isCustom": false,
            "notes": "",
            "isArchived": false
        },
        {
            "id": 67,
            "name": "Enges Bankdrücken",
            "primaryMuscleGroup": "TRIZEPS",
            "secondaryMuscleGroups": "BRUST,SCHULTERN",
            "category": "LANGHANTEL",
            "isCustom": false,
            "notes": "",
            "isArchived": false
        },
        {
            "id": 68,
            "name": "Plank",
            "primaryMuscleGroup": "CORE",
            "secondaryMuscleGroups": "",
            "category": "EIGENGEWICHT",
            "isCustom": false,
            "notes": "",
            "isArchived": false
        },
        {
            "id": 69,
            "name": "Cable Crunch",
            "primaryMuscleGroup": "CORE",
            "secondaryMuscleGroups": "",
            "category": "KABEL",
            "isCustom": false,
            "notes": "",
            "isArchived": false
        },
        {
            "id": 70,
            "name": "Beinheben hängend",
            "primaryMuscleGroup": "CORE",
            "secondaryMuscleGroups": "",
            "category": "EIGENGEWICHT",
            "isCustom": false,
            "notes": "",
            "isArchived": false
        },
        {
            "id": 71,
            "name": "Beinheben liegend",
            "primaryMuscleGroup": "CORE",
            "secondaryMuscleGroups": "",
            "category": "EIGENGEWICHT",
            "isCustom": false,
            "notes": "",
            "isArchived": false
        },
        {
            "id": 72,
            "name": "Russian Twist",
            "primaryMuscleGroup": "CORE",
            "secondaryMuscleGroups": "",
            "category": "EIGENGEWICHT",
            "isCustom": false,
            "notes": "",
            "isArchived": false
        },
        {
            "id": 73,
            "name": "Ab-Roller",
            "primaryMuscleGroup": "CORE",
            "secondaryMuscleGroups": "",
            "category": "EIGENGEWICHT",
            "isCustom": false,
            "notes": "",
            "isArchived": false
        },
        {
            "id": 74,
            "name": "Seitliches Plank",
            "primaryMuscleGroup": "CORE",
            "secondaryMuscleGroups": "",
            "category": "EIGENGEWICHT",
            "isCustom": false,
            "notes": "",
            "isArchived": false
        },
        {
            "id": 75,
            "name": "Unterarmcurls",
            "primaryMuscleGroup": "UNTERARME",
            "secondaryMuscleGroups": "",
            "category": "LANGHANTEL",
            "isCustom": false,
            "notes": "",
            "isArchived": false
        },
        {
            "id": 76,
            "name": "Reverse Curls",
            "primaryMuscleGroup": "UNTERARME",
            "secondaryMuscleGroups": "BIZEPS",
            "category": "LANGHANTEL",
            "isCustom": false,
            "notes": "",
            "isArchived": false
        },
        {
            "id": 77,
            "name": "Wadenheben stehend (Maschine)",
            "primaryMuscleGroup": "WADEN",
            "secondaryMuscleGroups": "",
            "category": "MASCHINE",
            "isCustom": false,
            "notes": "",
            "isArchived": false
        },
        {
            "id": 78,
            "name": "Wadenheben sitzend",
            "primaryMuscleGroup": "WADEN",
            "secondaryMuscleGroups": "",
            "category": "MASCHINE",
            "isCustom": false,
            "notes": "",
            "isArchived": false
        },
        {
            "id": 79,
            "name": "Wadenheben an der Beinpresse",
            "primaryMuscleGroup": "WADEN",
            "secondaryMuscleGroups": "",
            "category": "MASCHINE",
            "isCustom": false,
            "notes": "",
            "isArchived": false
        },
        {
            "id": 80,
            "name": "Landmine Press (einarmig)",
            "primaryMuscleGroup": "SCHULTERN",
            "secondaryMuscleGroups": "TRIZEPS,BRUST",
            "category": "LANGHANTEL",
            "isCustom": true,
            "notes": "Griff „neutral“ – 45° Winkel; Ellbogen nah am Körper",
            "isArchived": false
        },
        {
            "id": 81,
            "name": "Kabel-Crunch kniend",
            "primaryMuscleGroup": "CORE",
            "secondaryMuscleGroups": "",
            "category": "KABEL",
            "isCustom": true,
            "notes": "",
            "isArchived": false
        },
        {
            "id": 82,
            "name": "Beinpresse 45° (Studio Süd)",
            "primaryMuscleGroup": "BEINE",
            "secondaryMuscleGroups": "GESAESS",
            "category": "MASCHINE",
            "isCustom": true,
            "notes": "Studio gewechselt",
            "isArchived": true
        }
    ],
    "workoutSessions": [
        {
            "id": 1,
            "startTime": 1788246000000,
            "endTime": 1788249900000,
            "durationSeconds": 3900,
            "name": "Push A (Kraft)",
            "notes": "Gute Einheit – Schulter etwas steif.\nNächstes Mal länger aufwärmen.",
            "planId": 1,
            "metaPlanId": 1,
            "isDeload": false
        },
        {
            "id": 2,
            "startTime": 1788418800000,
            "endTime": 1788421800000,
            "durationSeconds": 3000,
            "name": "Pull B",
            "notes": "",
            "planId": 2,
            "metaPlanId": 1,
            "isDeload": false
        },
        {
            "id": 3,
            "startTime": 1788591600000,
            "endTime": 1788594300000,
            "durationSeconds": 2700,
            "name": "Push A (Kraft)",
            "notes": "",
            "planId": 1,
            "metaPlanId": 1,
            "isDeload": false
        },
        {
            "id": 4,
            "startTime": 1788800400000,
            "endTime": 1788802800000,
            "durationSeconds": 2400,
            "name": "Beine & Rumpf",
            "notes": "",
            "planId": 3,
            "metaPlanId": 1,
            "isDeload": true
        },
        {
            "id": 5,
            "startTime": 1788937200000,
            "endTime": 1788939000000,
            "durationSeconds": 1800,
            "name": "Push A (Kraft)",
            "notes": "",
            "planId": 1,
            "metaPlanId": null,
            "isDeload": null
        },
        {
            "id": 6,
            "startTime": 1789063200000,
            "endTime": 1789066800000,
            "durationSeconds": 3600,
            "name": "Freies Training",
            "notes": "Sonderzeichen-Test: \"Anführungszeichen\", Backslash \\ und Tab\tEnde",
            "planId": null,
            "metaPlanId": null,
            "isDeload": false
        },
        {
            "id": 7,
            "startTime": 1789126200000,
            "endTime": null,
            "durationSeconds": 0,
            "name": "Laufendes Training",
            "notes": "",
            "planId": null,
            "metaPlanId": null,
            "isDeload": null
        }
    ],
    "workoutSets": [
        {
            "id": 1,
            "sessionId": 1,
            "exerciseId": 1,
            "setNumber": 1,
            "reps": 8,
            "weightKg": 60.0,
            "setType": "WARMUP",
            "completedAt": 1788246120000,
            "rpe": null,
            "planTargetSnapshotId": 1
        },
        {
            "id": 2,
            "sessionId": 1,
            "exerciseId": 1,
            "setNumber": 2,
            "reps": 5,
            "weightKg": 100.0,
            "setType": "NORMAL",
            "completedAt": 1788246240000,
            "rpe": 7.0,
            "planTargetSnapshotId": 1
        },
        {
            "id": 3,
            "sessionId": 1,
            "exerciseId": 1,
            "setNumber": 3,
            "reps": 5,
            "weightKg": 100.0,
            "setType": "NORMAL",
            "completedAt": 1788246360000,
            "rpe": 7.5,
            "planTargetSnapshotId": 1
        },
        {
            "id": 4,
            "sessionId": 1,
            "exerciseId": 1,
            "setNumber": 4,
            "reps": 5,
            "weightKg": 100.0,
            "setType": "NORMAL",
            "completedAt": 1788246480000,
            "rpe": 8.0,
            "planTargetSnapshotId": 1
        },
        {
            "id": 5,
            "sessionId": 1,
            "exerciseId": 1,
            "setNumber": 5,
            "reps": 8,
            "weightKg": 80.0,
            "setType": "DROP_SET",
            "completedAt": 1788246600000,
            "rpe": 9.0,
            "planTargetSnapshotId": 1
        },
        {
            "id": 6,
            "sessionId": 1,
            "exerciseId": 3,
            "setNumber": 1,
            "reps": 12,
            "weightKg": 60.0,
            "setType": "NORMAL",
            "completedAt": 1788246720000,
            "rpe": 8.0,
            "planTargetSnapshotId": 2
        },
        {
            "id": 7,
            "sessionId": 1,
            "exerciseId": 3,
            "setNumber": 2,
            "reps": 12,
            "weightKg": 60.0,
            "setType": "NORMAL",
            "completedAt": 1788246840000,
            "rpe": 8.5,
            "planTargetSnapshotId": 2
        },
        {
            "id": 8,
            "sessionId": 1,
            "exerciseId": 3,
            "setNumber": 3,
            "reps": 12,
            "weightKg": 60.0,
            "setType": "NORMAL",
            "completedAt": 1788246960000,
            "rpe": 9.0,
            "planTargetSnapshotId": 2
        },
        {
            "id": 9,
            "sessionId": 1,
            "exerciseId": 80,
            "setNumber": 1,
            "reps": 13,
            "weightKg": 20.0,
            "setType": "NORMAL",
            "completedAt": 1788247080000,
            "rpe": null,
            "planTargetSnapshotId": 3
        },
        {
            "id": 10,
            "sessionId": 1,
            "exerciseId": 47,
            "setNumber": 1,
            "reps": 12,
            "weightKg": 10.0,
            "setType": "NORMAL",
            "completedAt": 1788247200000,
            "rpe": 8.0,
            "planTargetSnapshotId": 4
        },
        {
            "id": 11,
            "sessionId": 1,
            "exerciseId": 80,
            "setNumber": 2,
            "reps": 12,
            "weightKg": 20.0,
            "setType": "NORMAL",
            "completedAt": 1788247320000,
            "rpe": null,
            "planTargetSnapshotId": 3
        },
        {
            "id": 12,
            "sessionId": 1,
            "exerciseId": 47,
            "setNumber": 2,
            "reps": 12,
            "weightKg": 10.0,
            "setType": "NORMAL",
            "completedAt": 1788247440000,
            "rpe": 7.5,
            "planTargetSnapshotId": 4
        },
        {
            "id": 13,
            "sessionId": 1,
            "exerciseId": 80,
            "setNumber": 3,
            "reps": 12,
            "weightKg": 20.0,
            "setType": "NORMAL",
            "completedAt": 1788247560000,
            "rpe": null,
            "planTargetSnapshotId": 3
        },
        {
            "id": 14,
            "sessionId": 1,
            "exerciseId": 47,
            "setNumber": 3,
            "reps": 12,
            "weightKg": 10.0,
            "setType": "NORMAL",
            "completedAt": 1788247680000,
            "rpe": 8.5,
            "planTargetSnapshotId": 4
        },
        {
            "id": 15,
            "sessionId": 1,
            "exerciseId": 61,
            "setNumber": 1,
            "reps": 12,
            "weightKg": 20.0,
            "setType": "WARMUP",
            "completedAt": 1788247800000,
            "rpe": null,
            "planTargetSnapshotId": 5
        },
        {
            "id": 16,
            "sessionId": 1,
            "exerciseId": 61,
            "setNumber": 2,
            "reps": 10,
            "weightKg": 35.0,
            "setType": "NORMAL",
            "completedAt": 1788247920000,
            "rpe": null,
            "planTargetSnapshotId": 5
        },
        {
            "id": 17,
            "sessionId": 1,
            "exerciseId": 61,
            "setNumber": 3,
            "reps": 10,
            "weightKg": 35.0,
            "setType": "FAILURE",
            "completedAt": 1788248040000,
            "rpe": null,
            "planTargetSnapshotId": 5
        },
        {
            "id": 18,
            "sessionId": 1,
            "exerciseId": 61,
            "setNumber": 4,
            "reps": 15,
            "weightKg": 25.0,
            "setType": "BACKOFF",
            "completedAt": 1788248160000,
            "rpe": null,
            "planTargetSnapshotId": 5
        },
        {
            "id": 19,
            "sessionId": 2,
            "exerciseId": 13,
            "setNumber": 1,
            "reps": 8,
            "weightKg": 70.0,
            "setType": "NORMAL",
            "completedAt": 1788418920000,
            "rpe": null,
            "planTargetSnapshotId": 6
        },
        {
            "id": 20,
            "sessionId": 2,
            "exerciseId": 13,
            "setNumber": 2,
            "reps": 8,
            "weightKg": 70.0,
            "setType": "NORMAL",
            "completedAt": 1788419040000,
            "rpe": null,
            "planTargetSnapshotId": 6
        },
        {
            "id": 21,
            "sessionId": 2,
            "exerciseId": 13,
            "setNumber": 3,
            "reps": 7,
            "weightKg": 70.0,
            "setType": "NORMAL",
            "completedAt": 1788419160000,
            "rpe": null,
            "planTargetSnapshotId": 6
        },
        {
            "id": 22,
            "sessionId": 2,
            "exerciseId": 15,
            "setNumber": 1,
            "reps": 6,
            "weightKg": 0.0,
            "setType": "NORMAL",
            "completedAt": 1788419280000,
            "rpe": null,
            "planTargetSnapshotId": 7
        },
        {
            "id": 23,
            "sessionId": 2,
            "exerciseId": 15,
            "setNumber": 2,
            "reps": 6,
            "weightKg": 0.0,
            "setType": "NORMAL",
            "completedAt": 1788419400000,
            "rpe": null,
            "planTargetSnapshotId": 7
        },
        {
            "id": 24,
            "sessionId": 2,
            "exerciseId": 15,
            "setNumber": 3,
            "reps": 5,
            "weightKg": 0.0,
            "setType": "NORMAL",
            "completedAt": 1788419520000,
            "rpe": null,
            "planTargetSnapshotId": 7
        },
        {
            "id": 25,
            "sessionId": 2,
            "exerciseId": 15,
            "setNumber": 4,
            "reps": 4,
            "weightKg": 0.0,
            "setType": "FAILURE",
            "completedAt": 1788419640000,
            "rpe": null,
            "planTargetSnapshotId": 7
        },
        {
            "id": 26,
            "sessionId": 2,
            "exerciseId": 81,
            "setNumber": 1,
            "reps": 15,
            "weightKg": 30.0,
            "setType": "NORMAL",
            "completedAt": 1788419760000,
            "rpe": null,
            "planTargetSnapshotId": 8
        },
        {
            "id": 27,
            "sessionId": 2,
            "exerciseId": 81,
            "setNumber": 2,
            "reps": 15,
            "weightKg": 30.0,
            "setType": "NORMAL",
            "completedAt": 1788419880000,
            "rpe": null,
            "planTargetSnapshotId": 8
        },
        {
            "id": 28,
            "sessionId": 2,
            "exerciseId": 81,
            "setNumber": 3,
            "reps": 15,
            "weightKg": 30.0,
            "setType": "NORMAL",
            "completedAt": 1788420000000,
            "rpe": null,
            "planTargetSnapshotId": 8
        },
        {
            "id": 29,
            "sessionId": 3,
            "exerciseId": 1,
            "setNumber": 1,
            "reps": 5,
            "weightKg": 100.0,
            "setType": "NORMAL",
            "completedAt": 1788591720000,
            "rpe": 7.5,
            "planTargetSnapshotId": 9
        },
        {
            "id": 30,
            "sessionId": 3,
            "exerciseId": 1,
            "setNumber": 2,
            "reps": 5,
            "weightKg": 100.0,
            "setType": "NORMAL",
            "completedAt": 1788591840000,
            "rpe": 8.0,
            "planTargetSnapshotId": 9
        },
        {
            "id": 31,
            "sessionId": 3,
            "exerciseId": 1,
            "setNumber": 3,
            "reps": 5,
            "weightKg": 100.0,
            "setType": "NORMAL",
            "completedAt": 1788591960000,
            "rpe": 8.5,
            "planTargetSnapshotId": 9
        },
        {
            "id": 32,
            "sessionId": 3,
            "exerciseId": 3,
            "setNumber": 1,
            "reps": 8,
            "weightKg": 62.5,
            "setType": "NORMAL",
            "completedAt": 1788592080000,
            "rpe": null,
            "planTargetSnapshotId": 10
        },
        {
            "id": 33,
            "sessionId": 3,
            "exerciseId": 3,
            "setNumber": 2,
            "reps": 8,
            "weightKg": 62.5,
            "setType": "NORMAL",
            "completedAt": 1788592200000,
            "rpe": null,
            "planTargetSnapshotId": 10
        },
        {
            "id": 34,
            "sessionId": 3,
            "exerciseId": 3,
            "setNumber": 3,
            "reps": 7,
            "weightKg": 62.5,
            "setType": "NORMAL",
            "completedAt": 1788592320000,
            "rpe": null,
            "planTargetSnapshotId": 10
        },
        {
            "id": 35,
            "sessionId": 3,
            "exerciseId": 80,
            "setNumber": 1,
            "reps": 11,
            "weightKg": 22.5,
            "setType": "NORMAL",
            "completedAt": 1788592440000,
            "rpe": null,
            "planTargetSnapshotId": 11
        },
        {
            "id": 36,
            "sessionId": 4,
            "exerciseId": 26,
            "setNumber": 1,
            "reps": 5,
            "weightKg": 60.0,
            "setType": "WARMUP",
            "completedAt": 1788800520000,
            "rpe": null,
            "planTargetSnapshotId": 14
        },
        {
            "id": 37,
            "sessionId": 4,
            "exerciseId": 26,
            "setNumber": 2,
            "reps": 5,
            "weightKg": 90.0,
            "setType": "NORMAL",
            "completedAt": 1788800640000,
            "rpe": null,
            "planTargetSnapshotId": 14
        },
        {
            "id": 38,
            "sessionId": 4,
            "exerciseId": 26,
            "setNumber": 3,
            "reps": 5,
            "weightKg": 90.0,
            "setType": "NORMAL",
            "completedAt": 1788800760000,
            "rpe": null,
            "planTargetSnapshotId": 14
        },
        {
            "id": 39,
            "sessionId": 4,
            "exerciseId": 26,
            "setNumber": 4,
            "reps": 5,
            "weightKg": 90.0,
            "setType": "NORMAL",
            "completedAt": 1788800880000,
            "rpe": null,
            "planTargetSnapshotId": 14
        },
        {
            "id": 40,
            "sessionId": 5,
            "exerciseId": 1,
            "setNumber": 1,
            "reps": 5,
            "weightKg": 100.0,
            "setType": "NORMAL",
            "completedAt": 1788937320000,
            "rpe": 8.0,
            "planTargetSnapshotId": 15
        },
        {
            "id": 41,
            "sessionId": 5,
            "exerciseId": 1,
            "setNumber": 2,
            "reps": 5,
            "weightKg": 100.0,
            "setType": "NORMAL",
            "completedAt": 1788937440000,
            "rpe": 8.0,
            "planTargetSnapshotId": 15
        },
        {
            "id": 42,
            "sessionId": 5,
            "exerciseId": 1,
            "setNumber": 3,
            "reps": 5,
            "weightKg": 100.0,
            "setType": "NORMAL",
            "completedAt": 1788937560000,
            "rpe": 8.5,
            "planTargetSnapshotId": 15
        },
        {
            "id": 43,
            "sessionId": 6,
            "exerciseId": 22,
            "setNumber": 1,
            "reps": 5,
            "weightKg": 100.0,
            "setType": "WARMUP",
            "completedAt": 1789063320000,
            "rpe": null,
            "planTargetSnapshotId": null
        },
        {
            "id": 44,
            "sessionId": 6,
            "exerciseId": 22,
            "setNumber": 2,
            "reps": 5,
            "weightKg": 140.0,
            "setType": "NORMAL",
            "completedAt": 1789063440000,
            "rpe": 8.0,
            "planTargetSnapshotId": null
        },
        {
            "id": 45,
            "sessionId": 6,
            "exerciseId": 22,
            "setNumber": 3,
            "reps": 0,
            "weightKg": 160.0,
            "setType": "NORMAL",
            "completedAt": 1789063560000,
            "rpe": null,
            "planTargetSnapshotId": null
        },
        {
            "id": 46,
            "sessionId": 6,
            "exerciseId": 22,
            "setNumber": 4,
            "reps": 8,
            "weightKg": 110.0,
            "setType": "BACKOFF",
            "completedAt": 1789063680000,
            "rpe": null,
            "planTargetSnapshotId": null
        },
        {
            "id": 47,
            "sessionId": 6,
            "exerciseId": 82,
            "setNumber": 1,
            "reps": 12,
            "weightKg": 180.0,
            "setType": "NORMAL",
            "completedAt": 1789063800000,
            "rpe": null,
            "planTargetSnapshotId": null
        },
        {
            "id": 48,
            "sessionId": 6,
            "exerciseId": 82,
            "setNumber": 2,
            "reps": 10,
            "weightKg": 200.0,
            "setType": "DROP_SET",
            "completedAt": 1789063920000,
            "rpe": null,
            "planTargetSnapshotId": null
        },
        {
            "id": 49,
            "sessionId": 7,
            "exerciseId": 15,
            "setNumber": 1,
            "reps": 8,
            "weightKg": 0.0,
            "setType": "NORMAL",
            "completedAt": 1789126320000,
            "rpe": null,
            "planTargetSnapshotId": null
        }
    ],
    "trainingPlans": [
        {
            "id": 1,
            "name": "Push A (Kraft)",
            "createdAt": 1788243000000
        },
        {
            "id": 2,
            "name": "Pull B",
            "createdAt": 1788243300000
        },
        {
            "id": 3,
            "name": "Beine & Rumpf",
            "createdAt": 1788243600000
        }
    ],
    "planExercises": [
        {
            "id": 1,
            "planId": 1,
            "exerciseId": 1,
            "orderIndex": 0,
            "supersetGroupId": null,
            "targetSets": 3,
            "targetReps": 5,
            "targetWeightKg": 100.0,
            "progression": {
                "scheme": "LINEAR",
                "incrementValue": 2.5,
                "incrementUnit": "METRIC",
                "incrementKg": 2.5,
                "minReps": null,
                "maxReps": null,
                "targetTotalReps": null,
                "targetRpe": null,
                "rpeTolerance": null,
                "stallThreshold": 2,
                "backoffPercent": 10.0,
                "ruleRevision": 2,
                "successThreshold": 2
            },
            "setTargets": []
        },
        {
            "id": 2,
            "planId": 1,
            "exerciseId": 3,
            "orderIndex": 1,
            "supersetGroupId": null,
            "targetSets": 3,
            "targetReps": 8,
            "targetWeightKg": 62.5,
            "progression": {
                "scheme": "DOUBLE",
                "incrementValue": 2.5,
                "incrementUnit": "METRIC",
                "incrementKg": 2.5,
                "minReps": 8,
                "maxReps": 12,
                "targetTotalReps": null,
                "targetRpe": null,
                "rpeTolerance": null,
                "stallThreshold": 2,
                "backoffPercent": 10.0,
                "ruleRevision": 2,
                "successThreshold": 1
            },
            "setTargets": []
        },
        {
            "id": 3,
            "planId": 1,
            "exerciseId": 80,
            "orderIndex": 2,
            "supersetGroupId": 1,
            "targetSets": 3,
            "targetReps": 11,
            "targetWeightKg": 22.267961850050177,
            "progression": {
                "scheme": "TOTAL_REPS",
                "incrementValue": 5.0,
                "incrementUnit": "IMPERIAL",
                "incrementKg": 2.267961850050177,
                "minReps": null,
                "maxReps": null,
                "targetTotalReps": 36,
                "targetRpe": null,
                "rpeTolerance": null,
                "stallThreshold": 3,
                "backoffPercent": 7.5,
                "ruleRevision": 2,
                "successThreshold": 1
            },
            "setTargets": []
        },
        {
            "id": 4,
            "planId": 1,
            "exerciseId": 47,
            "orderIndex": 3,
            "supersetGroupId": 1,
            "targetSets": 3,
            "targetReps": 12,
            "targetWeightKg": 10.0,
            "progression": {
                "scheme": "RPE_RIR",
                "incrementValue": 1.0,
                "incrementUnit": "METRIC",
                "incrementKg": 1.0,
                "minReps": null,
                "maxReps": null,
                "targetTotalReps": null,
                "targetRpe": 8.0,
                "rpeTolerance": 0.5,
                "stallThreshold": 2,
                "backoffPercent": 10.0,
                "ruleRevision": 2,
                "successThreshold": 1
            },
            "setTargets": []
        },
        {
            "id": 5,
            "planId": 1,
            "exerciseId": 61,
            "orderIndex": 4,
            "supersetGroupId": null,
            "targetSets": 3,
            "targetReps": 10,
            "targetWeightKg": 35.0,
            "progression": {
                "scheme": "MANUAL",
                "incrementValue": null,
                "incrementUnit": null,
                "incrementKg": null,
                "minReps": null,
                "maxReps": null,
                "targetTotalReps": null,
                "targetRpe": null,
                "rpeTolerance": null,
                "stallThreshold": 2,
                "backoffPercent": 10.0,
                "ruleRevision": 1,
                "successThreshold": 1
            },
            "setTargets": [
                {
                    "kind": "WARMUP",
                    "reps": 12,
                    "weightKg": 20.0
                },
                {
                    "kind": "NORMAL",
                    "reps": 10,
                    "weightKg": 35.0
                },
                {
                    "kind": "NORMAL",
                    "reps": 10,
                    "weightKg": 35.0
                },
                {
                    "kind": "BACKOFF",
                    "reps": 15,
                    "weightKg": 25.0
                }
            ]
        },
        {
            "id": 6,
            "planId": 2,
            "exerciseId": 13,
            "orderIndex": 0,
            "supersetGroupId": null,
            "targetSets": 3,
            "targetReps": 8,
            "targetWeightKg": 70.0,
            "progression": {
                "scheme": "LINEAR",
                "incrementValue": 2.5,
                "incrementUnit": "METRIC",
                "incrementKg": 2.5,
                "minReps": null,
                "maxReps": null,
                "targetTotalReps": null,
                "targetRpe": null,
                "rpeTolerance": null,
                "stallThreshold": 2,
                "backoffPercent": 10.0,
                "ruleRevision": 1,
                "successThreshold": 1
            },
            "setTargets": []
        },
        {
            "id": 7,
            "planId": 2,
            "exerciseId": 15,
            "orderIndex": 1,
            "supersetGroupId": null,
            "targetSets": 3,
            "targetReps": 6,
            "targetWeightKg": 0.0,
            "progression": {
                "scheme": "MANUAL",
                "incrementValue": null,
                "incrementUnit": null,
                "incrementKg": null,
                "minReps": null,
                "maxReps": null,
                "targetTotalReps": null,
                "targetRpe": null,
                "rpeTolerance": null,
                "stallThreshold": 2,
                "backoffPercent": 10.0,
                "ruleRevision": 1,
                "successThreshold": 1
            },
            "setTargets": []
        },
        {
            "id": 8,
            "planId": 2,
            "exerciseId": 81,
            "orderIndex": 2,
            "supersetGroupId": null,
            "targetSets": 3,
            "targetReps": 15,
            "targetWeightKg": 30.0,
            "progression": {
                "scheme": "LINEAR",
                "incrementValue": 2.5,
                "incrementUnit": "METRIC",
                "incrementKg": 2.5,
                "minReps": null,
                "maxReps": null,
                "targetTotalReps": null,
                "targetRpe": null,
                "rpeTolerance": null,
                "stallThreshold": 2,
                "backoffPercent": 10.0,
                "ruleRevision": 3,
                "successThreshold": 1
            },
            "setTargets": []
        },
        {
            "id": 9,
            "planId": 3,
            "exerciseId": 26,
            "orderIndex": 0,
            "supersetGroupId": null,
            "targetSets": 3,
            "targetReps": 5,
            "targetWeightKg": 120.0,
            "progression": {
                "scheme": "LINEAR",
                "incrementValue": 5.0,
                "incrementUnit": "METRIC",
                "incrementKg": 5.0,
                "minReps": null,
                "maxReps": null,
                "targetTotalReps": null,
                "targetRpe": null,
                "rpeTolerance": null,
                "stallThreshold": 2,
                "backoffPercent": 10.0,
                "ruleRevision": 2,
                "successThreshold": 1
            },
            "setTargets": []
        }
    ],
    "personalRecords": [
        {
            "id": 1,
            "exerciseId": 1,
            "type": "MAX_WEIGHT",
            "value": 100.0,
            "achievedAt": 1788937560000
        },
        {
            "id": 2,
            "exerciseId": 1,
            "type": "MAX_REPS",
            "value": 8.0,
            "achievedAt": 1788246600000
        },
        {
            "id": 3,
            "exerciseId": 1,
            "type": "MAX_E1RM",
            "value": 116.66666666666667,
            "achievedAt": 1788937560000
        },
        {
            "id": 4,
            "exerciseId": 3,
            "type": "MAX_WEIGHT",
            "value": 62.5,
            "achievedAt": 1788592320000
        },
        {
            "id": 5,
            "exerciseId": 3,
            "type": "MAX_REPS",
            "value": 12.0,
            "achievedAt": 1788246960000
        },
        {
            "id": 6,
            "exerciseId": 3,
            "type": "MAX_E1RM",
            "value": 84.0,
            "achievedAt": 1788246960000
        },
        {
            "id": 7,
            "exerciseId": 80,
            "type": "MAX_WEIGHT",
            "value": 22.5,
            "achievedAt": 1788592440000
        },
        {
            "id": 8,
            "exerciseId": 80,
            "type": "MAX_REPS",
            "value": 13.0,
            "achievedAt": 1788247080000
        },
        {
            "id": 9,
            "exerciseId": 80,
            "type": "MAX_E1RM",
            "value": 30.75,
            "achievedAt": 1788592440000
        },
        {
            "id": 10,
            "exerciseId": 47,
            "type": "MAX_WEIGHT",
            "value": 10.0,
            "achievedAt": 1788247680000
        },
        {
            "id": 11,
            "exerciseId": 47,
            "type": "MAX_REPS",
            "value": 12.0,
            "achievedAt": 1788247680000
        },
        {
            "id": 12,
            "exerciseId": 47,
            "type": "MAX_E1RM",
            "value": 14.0,
            "achievedAt": 1788247680000
        },
        {
            "id": 13,
            "exerciseId": 61,
            "type": "MAX_WEIGHT",
            "value": 35.0,
            "achievedAt": 1788248040000
        },
        {
            "id": 14,
            "exerciseId": 61,
            "type": "MAX_REPS",
            "value": 15.0,
            "achievedAt": 1788248160000
        },
        {
            "id": 15,
            "exerciseId": 61,
            "type": "MAX_E1RM",
            "value": 46.666666666666664,
            "achievedAt": 1788248040000
        },
        {
            "id": 16,
            "exerciseId": 1,
            "type": "MAX_VOLUME",
            "value": 2140.0,
            "achievedAt": 1788246600000
        },
        {
            "id": 17,
            "exerciseId": 3,
            "type": "MAX_VOLUME",
            "value": 2160.0,
            "achievedAt": 1788246960000
        },
        {
            "id": 18,
            "exerciseId": 47,
            "type": "MAX_VOLUME",
            "value": 360.0,
            "achievedAt": 1788247680000
        },
        {
            "id": 19,
            "exerciseId": 61,
            "type": "MAX_VOLUME",
            "value": 1075.0,
            "achievedAt": 1788248160000
        },
        {
            "id": 20,
            "exerciseId": 80,
            "type": "MAX_VOLUME",
            "value": 740.0,
            "achievedAt": 1788247560000
        },
        {
            "id": 21,
            "exerciseId": 13,
            "type": "MAX_WEIGHT",
            "value": 70.0,
            "achievedAt": 1788419160000
        },
        {
            "id": 22,
            "exerciseId": 13,
            "type": "MAX_REPS",
            "value": 8.0,
            "achievedAt": 1788419040000
        },
        {
            "id": 23,
            "exerciseId": 13,
            "type": "MAX_E1RM",
            "value": 88.66666666666666,
            "achievedAt": 1788419040000
        },
        {
            "id": 24,
            "exerciseId": 15,
            "type": "MAX_REPS",
            "value": 8.0,
            "achievedAt": 1789126320000
        },
        {
            "id": 25,
            "exerciseId": 81,
            "type": "MAX_WEIGHT",
            "value": 30.0,
            "achievedAt": 1788420000000
        },
        {
            "id": 26,
            "exerciseId": 81,
            "type": "MAX_REPS",
            "value": 15.0,
            "achievedAt": 1788420000000
        },
        {
            "id": 27,
            "exerciseId": 81,
            "type": "MAX_E1RM",
            "value": 45.0,
            "achievedAt": 1788420000000
        },
        {
            "id": 28,
            "exerciseId": 13,
            "type": "MAX_VOLUME",
            "value": 1610.0,
            "achievedAt": 1788419160000
        },
        {
            "id": 29,
            "exerciseId": 81,
            "type": "MAX_VOLUME",
            "value": 1350.0,
            "achievedAt": 1788420000000
        },
        {
            "id": 30,
            "exerciseId": 26,
            "type": "MAX_WEIGHT",
            "value": 90.0,
            "achievedAt": 1788800880000
        },
        {
            "id": 31,
            "exerciseId": 26,
            "type": "MAX_REPS",
            "value": 5.0,
            "achievedAt": 1788800880000
        },
        {
            "id": 32,
            "exerciseId": 26,
            "type": "MAX_E1RM",
            "value": 105.0,
            "achievedAt": 1788800880000
        },
        {
            "id": 33,
            "exerciseId": 26,
            "type": "MAX_VOLUME",
            "value": 1350.0,
            "achievedAt": 1788800880000
        },
        {
            "id": 34,
            "exerciseId": 22,
            "type": "MAX_WEIGHT",
            "value": 160.0,
            "achievedAt": 1789063560000
        },
        {
            "id": 35,
            "exerciseId": 22,
            "type": "MAX_REPS",
            "value": 8.0,
            "achievedAt": 1789063680000
        },
        {
            "id": 36,
            "exerciseId": 22,
            "type": "MAX_E1RM",
            "value": 163.33333333333334,
            "achievedAt": 1789063440000
        },
        {
            "id": 37,
            "exerciseId": 82,
            "type": "MAX_WEIGHT",
            "value": 200.0,
            "achievedAt": 1789063920000
        },
        {
            "id": 38,
            "exerciseId": 82,
            "type": "MAX_REPS",
            "value": 12.0,
            "achievedAt": 1789063800000
        },
        {
            "id": 39,
            "exerciseId": 82,
            "type": "MAX_E1RM",
            "value": 266.66666666666663,
            "achievedAt": 1789063920000
        },
        {
            "id": 40,
            "exerciseId": 22,
            "type": "MAX_VOLUME",
            "value": 1580.0,
            "achievedAt": 1789063680000
        },
        {
            "id": 41,
            "exerciseId": 82,
            "type": "MAX_VOLUME",
            "value": 4160.0,
            "achievedAt": 1789063920000
        }
    ],
    "metaTrainingPlans": [
        {
            "id": 1,
            "name": "Ganzkörper-Rotation",
            "createdAt": 1788243900000
        }
    ],
    "metaPlanItems": [
        {
            "id": 1,
            "metaPlanId": 1,
            "trainingPlanId": 1,
            "orderIndex": 0
        },
        {
            "id": 2,
            "metaPlanId": 1,
            "trainingPlanId": 2,
            "orderIndex": 1
        },
        {
            "id": 3,
            "metaPlanId": 1,
            "trainingPlanId": 3,
            "orderIndex": 2
        }
    ],
    "metaPlanSkips": [
        {
            "id": 1,
            "metaPlanId": 1,
            "trainingPlanId": 3,
            "skippedAt": 1788512400000
        }
    ],
    "workoutPlanTargets": [
        {
            "id": 1,
            "sessionId": 1,
            "planId": 1,
            "exerciseId": 1,
            "orderIndex": 0,
            "supersetGroupId": null,
            "target": {
                "sets": 3,
                "reps": 5,
                "weightKg": 100.0
            },
            "progression": {
                "scheme": "LINEAR",
                "incrementValue": 2.5,
                "incrementUnit": "METRIC",
                "incrementKg": 2.5,
                "minReps": null,
                "maxReps": null,
                "targetTotalReps": null,
                "targetRpe": null,
                "rpeTolerance": null,
                "stallThreshold": 2,
                "backoffPercent": 10.0,
                "ruleRevision": 2,
                "successThreshold": 2
            },
            "setTargets": []
        },
        {
            "id": 2,
            "sessionId": 1,
            "planId": 1,
            "exerciseId": 3,
            "orderIndex": 1,
            "supersetGroupId": null,
            "target": {
                "sets": 3,
                "reps": 8,
                "weightKg": 60.0
            },
            "progression": {
                "scheme": "DOUBLE",
                "incrementValue": 2.5,
                "incrementUnit": "METRIC",
                "incrementKg": 2.5,
                "minReps": 8,
                "maxReps": 12,
                "targetTotalReps": null,
                "targetRpe": null,
                "rpeTolerance": null,
                "stallThreshold": 2,
                "backoffPercent": 10.0,
                "ruleRevision": 2,
                "successThreshold": 1
            },
            "setTargets": []
        },
        {
            "id": 3,
            "sessionId": 1,
            "planId": 1,
            "exerciseId": 80,
            "orderIndex": 2,
            "supersetGroupId": 1,
            "target": {
                "sets": 3,
                "reps": 10,
                "weightKg": 20.0
            },
            "progression": {
                "scheme": "TOTAL_REPS",
                "incrementValue": 5.0,
                "incrementUnit": "IMPERIAL",
                "incrementKg": 2.267961850050177,
                "minReps": null,
                "maxReps": null,
                "targetTotalReps": 36,
                "targetRpe": null,
                "rpeTolerance": null,
                "stallThreshold": 3,
                "backoffPercent": 7.5,
                "ruleRevision": 2,
                "successThreshold": 1
            },
            "setTargets": []
        },
        {
            "id": 4,
            "sessionId": 1,
            "planId": 1,
            "exerciseId": 47,
            "orderIndex": 3,
            "supersetGroupId": 1,
            "target": {
                "sets": 3,
                "reps": 12,
                "weightKg": 10.0
            },
            "progression": {
                "scheme": "RPE_RIR",
                "incrementValue": 1.0,
                "incrementUnit": "METRIC",
                "incrementKg": 1.0,
                "minReps": null,
                "maxReps": null,
                "targetTotalReps": null,
                "targetRpe": 8.0,
                "rpeTolerance": 0.5,
                "stallThreshold": 2,
                "backoffPercent": 10.0,
                "ruleRevision": 2,
                "successThreshold": 1
            },
            "setTargets": []
        },
        {
            "id": 5,
            "sessionId": 1,
            "planId": 1,
            "exerciseId": 61,
            "orderIndex": 4,
            "supersetGroupId": null,
            "target": {
                "sets": 3,
                "reps": 10,
                "weightKg": 35.0
            },
            "progression": {
                "scheme": "MANUAL",
                "incrementValue": null,
                "incrementUnit": null,
                "incrementKg": null,
                "minReps": null,
                "maxReps": null,
                "targetTotalReps": null,
                "targetRpe": null,
                "rpeTolerance": null,
                "stallThreshold": 2,
                "backoffPercent": 10.0,
                "ruleRevision": 1,
                "successThreshold": 1
            },
            "setTargets": [
                {
                    "kind": "WARMUP",
                    "reps": 12,
                    "weightKg": 20.0
                },
                {
                    "kind": "NORMAL",
                    "reps": 10,
                    "weightKg": 35.0
                },
                {
                    "kind": "NORMAL",
                    "reps": 10,
                    "weightKg": 35.0
                },
                {
                    "kind": "BACKOFF",
                    "reps": 15,
                    "weightKg": 25.0
                }
            ]
        },
        {
            "id": 6,
            "sessionId": 2,
            "planId": 2,
            "exerciseId": 13,
            "orderIndex": 0,
            "supersetGroupId": null,
            "target": {
                "sets": 3,
                "reps": 8,
                "weightKg": 70.0
            },
            "progression": {
                "scheme": "LINEAR",
                "incrementValue": 2.5,
                "incrementUnit": "METRIC",
                "incrementKg": 2.5,
                "minReps": null,
                "maxReps": null,
                "targetTotalReps": null,
                "targetRpe": null,
                "rpeTolerance": null,
                "stallThreshold": 2,
                "backoffPercent": 10.0,
                "ruleRevision": 1,
                "successThreshold": 1
            },
            "setTargets": []
        },
        {
            "id": 7,
            "sessionId": 2,
            "planId": 2,
            "exerciseId": 15,
            "orderIndex": 1,
            "supersetGroupId": null,
            "target": {
                "sets": 3,
                "reps": 6,
                "weightKg": 0.0
            },
            "progression": {
                "scheme": "MANUAL",
                "incrementValue": null,
                "incrementUnit": null,
                "incrementKg": null,
                "minReps": null,
                "maxReps": null,
                "targetTotalReps": null,
                "targetRpe": null,
                "rpeTolerance": null,
                "stallThreshold": 2,
                "backoffPercent": 10.0,
                "ruleRevision": 1,
                "successThreshold": 1
            },
            "setTargets": []
        },
        {
            "id": 8,
            "sessionId": 2,
            "planId": 2,
            "exerciseId": 81,
            "orderIndex": 2,
            "supersetGroupId": null,
            "target": {
                "sets": 3,
                "reps": 15,
                "weightKg": 30.0
            },
            "progression": {
                "scheme": "LINEAR",
                "incrementValue": 2.5,
                "incrementUnit": "METRIC",
                "incrementKg": 2.5,
                "minReps": null,
                "maxReps": null,
                "targetTotalReps": null,
                "targetRpe": null,
                "rpeTolerance": null,
                "stallThreshold": 2,
                "backoffPercent": 10.0,
                "ruleRevision": 3,
                "successThreshold": 1
            },
            "setTargets": []
        },
        {
            "id": 9,
            "sessionId": 3,
            "planId": 1,
            "exerciseId": 1,
            "orderIndex": 0,
            "supersetGroupId": null,
            "target": {
                "sets": 3,
                "reps": 5,
                "weightKg": 100.0
            },
            "progression": {
                "scheme": "LINEAR",
                "incrementValue": 2.5,
                "incrementUnit": "METRIC",
                "incrementKg": 2.5,
                "minReps": null,
                "maxReps": null,
                "targetTotalReps": null,
                "targetRpe": null,
                "rpeTolerance": null,
                "stallThreshold": 2,
                "backoffPercent": 10.0,
                "ruleRevision": 2,
                "successThreshold": 2
            },
            "setTargets": []
        },
        {
            "id": 10,
            "sessionId": 3,
            "planId": 1,
            "exerciseId": 3,
            "orderIndex": 1,
            "supersetGroupId": null,
            "target": {
                "sets": 3,
                "reps": 8,
                "weightKg": 62.5
            },
            "progression": {
                "scheme": "DOUBLE",
                "incrementValue": 2.5,
                "incrementUnit": "METRIC",
                "incrementKg": 2.5,
                "minReps": 8,
                "maxReps": 12,
                "targetTotalReps": null,
                "targetRpe": null,
                "rpeTolerance": null,
                "stallThreshold": 2,
                "backoffPercent": 10.0,
                "ruleRevision": 2,
                "successThreshold": 1
            },
            "setTargets": []
        },
        {
            "id": 11,
            "sessionId": 3,
            "planId": 1,
            "exerciseId": 80,
            "orderIndex": 2,
            "supersetGroupId": 1,
            "target": {
                "sets": 3,
                "reps": 11,
                "weightKg": 22.267961850050177
            },
            "progression": {
                "scheme": "TOTAL_REPS",
                "incrementValue": 5.0,
                "incrementUnit": "IMPERIAL",
                "incrementKg": 2.267961850050177,
                "minReps": null,
                "maxReps": null,
                "targetTotalReps": 36,
                "targetRpe": null,
                "rpeTolerance": null,
                "stallThreshold": 3,
                "backoffPercent": 7.5,
                "ruleRevision": 2,
                "successThreshold": 1
            },
            "setTargets": []
        },
        {
            "id": 12,
            "sessionId": 3,
            "planId": 1,
            "exerciseId": 47,
            "orderIndex": 3,
            "supersetGroupId": 1,
            "target": {
                "sets": 3,
                "reps": 12,
                "weightKg": 10.0
            },
            "progression": {
                "scheme": "RPE_RIR",
                "incrementValue": 1.0,
                "incrementUnit": "METRIC",
                "incrementKg": 1.0,
                "minReps": null,
                "maxReps": null,
                "targetTotalReps": null,
                "targetRpe": 8.0,
                "rpeTolerance": 0.5,
                "stallThreshold": 2,
                "backoffPercent": 10.0,
                "ruleRevision": 2,
                "successThreshold": 1
            },
            "setTargets": []
        },
        {
            "id": 13,
            "sessionId": 3,
            "planId": 1,
            "exerciseId": 61,
            "orderIndex": 4,
            "supersetGroupId": null,
            "target": {
                "sets": 3,
                "reps": 10,
                "weightKg": 35.0
            },
            "progression": {
                "scheme": "MANUAL",
                "incrementValue": null,
                "incrementUnit": null,
                "incrementKg": null,
                "minReps": null,
                "maxReps": null,
                "targetTotalReps": null,
                "targetRpe": null,
                "rpeTolerance": null,
                "stallThreshold": 2,
                "backoffPercent": 10.0,
                "ruleRevision": 1,
                "successThreshold": 1
            },
            "setTargets": [
                {
                    "kind": "WARMUP",
                    "reps": 12,
                    "weightKg": 20.0
                },
                {
                    "kind": "NORMAL",
                    "reps": 10,
                    "weightKg": 35.0
                },
                {
                    "kind": "NORMAL",
                    "reps": 10,
                    "weightKg": 35.0
                },
                {
                    "kind": "BACKOFF",
                    "reps": 15,
                    "weightKg": 25.0
                }
            ]
        },
        {
            "id": 14,
            "sessionId": 4,
            "planId": 3,
            "exerciseId": 26,
            "orderIndex": 0,
            "supersetGroupId": null,
            "target": {
                "sets": 3,
                "reps": 5,
                "weightKg": 120.0
            },
            "progression": {
                "scheme": "LINEAR",
                "incrementValue": 5.0,
                "incrementUnit": "METRIC",
                "incrementKg": 5.0,
                "minReps": null,
                "maxReps": null,
                "targetTotalReps": null,
                "targetRpe": null,
                "rpeTolerance": null,
                "stallThreshold": 2,
                "backoffPercent": 10.0,
                "ruleRevision": 2,
                "successThreshold": 1
            },
            "setTargets": []
        },
        {
            "id": 15,
            "sessionId": 5,
            "planId": 1,
            "exerciseId": 1,
            "orderIndex": 0,
            "supersetGroupId": null,
            "target": {
                "sets": 3,
                "reps": 5,
                "weightKg": 100.0
            },
            "progression": {
                "scheme": "LINEAR",
                "incrementValue": 2.5,
                "incrementUnit": "METRIC",
                "incrementKg": 2.5,
                "minReps": null,
                "maxReps": null,
                "targetTotalReps": null,
                "targetRpe": null,
                "rpeTolerance": null,
                "stallThreshold": 2,
                "backoffPercent": 10.0,
                "ruleRevision": 2,
                "successThreshold": 2
            },
            "setTargets": []
        },
        {
            "id": 16,
            "sessionId": 5,
            "planId": 1,
            "exerciseId": 3,
            "orderIndex": 1,
            "supersetGroupId": null,
            "target": {
                "sets": 3,
                "reps": 8,
                "weightKg": 62.5
            },
            "progression": {
                "scheme": "DOUBLE",
                "incrementValue": 2.5,
                "incrementUnit": "METRIC",
                "incrementKg": 2.5,
                "minReps": 8,
                "maxReps": 12,
                "targetTotalReps": null,
                "targetRpe": null,
                "rpeTolerance": null,
                "stallThreshold": 2,
                "backoffPercent": 10.0,
                "ruleRevision": 2,
                "successThreshold": 1
            },
            "setTargets": []
        },
        {
            "id": 17,
            "sessionId": 5,
            "planId": 1,
            "exerciseId": 80,
            "orderIndex": 2,
            "supersetGroupId": 1,
            "target": {
                "sets": 3,
                "reps": 11,
                "weightKg": 22.267961850050177
            },
            "progression": {
                "scheme": "TOTAL_REPS",
                "incrementValue": 5.0,
                "incrementUnit": "IMPERIAL",
                "incrementKg": 2.267961850050177,
                "minReps": null,
                "maxReps": null,
                "targetTotalReps": 36,
                "targetRpe": null,
                "rpeTolerance": null,
                "stallThreshold": 3,
                "backoffPercent": 7.5,
                "ruleRevision": 2,
                "successThreshold": 1
            },
            "setTargets": []
        },
        {
            "id": 18,
            "sessionId": 5,
            "planId": 1,
            "exerciseId": 47,
            "orderIndex": 3,
            "supersetGroupId": 1,
            "target": {
                "sets": 3,
                "reps": 12,
                "weightKg": 10.0
            },
            "progression": {
                "scheme": "RPE_RIR",
                "incrementValue": 1.0,
                "incrementUnit": "METRIC",
                "incrementKg": 1.0,
                "minReps": null,
                "maxReps": null,
                "targetTotalReps": null,
                "targetRpe": 8.0,
                "rpeTolerance": 0.5,
                "stallThreshold": 2,
                "backoffPercent": 10.0,
                "ruleRevision": 2,
                "successThreshold": 1
            },
            "setTargets": []
        },
        {
            "id": 19,
            "sessionId": 5,
            "planId": 1,
            "exerciseId": 61,
            "orderIndex": 4,
            "supersetGroupId": null,
            "target": {
                "sets": 3,
                "reps": 10,
                "weightKg": 35.0
            },
            "progression": {
                "scheme": "MANUAL",
                "incrementValue": null,
                "incrementUnit": null,
                "incrementKg": null,
                "minReps": null,
                "maxReps": null,
                "targetTotalReps": null,
                "targetRpe": null,
                "rpeTolerance": null,
                "stallThreshold": 2,
                "backoffPercent": 10.0,
                "ruleRevision": 1,
                "successThreshold": 1
            },
            "setTargets": [
                {
                    "kind": "WARMUP",
                    "reps": 12,
                    "weightKg": 20.0
                },
                {
                    "kind": "NORMAL",
                    "reps": 10,
                    "weightKg": 35.0
                },
                {
                    "kind": "NORMAL",
                    "reps": 10,
                    "weightKg": 35.0
                },
                {
                    "kind": "BACKOFF",
                    "reps": 15,
                    "weightKg": 25.0
                }
            ]
        }
    ],
    "progressionSuggestions": [
        {
            "id": 1,
            "sourceSessionId": 1,
            "sourceTargetSnapshotId": 1,
            "planId": 1,
            "exerciseId": 1,
            "orderIndex": 0,
            "supersetGroupId": null,
            "sourceTarget": {
                "sets": 3,
                "reps": 5,
                "weightKg": 100.0
            },
            "sourceProgression": {
                "scheme": "LINEAR",
                "incrementValue": 2.5,
                "incrementUnit": "METRIC",
                "incrementKg": 2.5,
                "minReps": null,
                "maxReps": null,
                "targetTotalReps": null,
                "targetRpe": null,
                "rpeTolerance": null,
                "stallThreshold": 2,
                "backoffPercent": 10.0,
                "ruleRevision": 2,
                "successThreshold": 2
            },
            "outcomeType": "KEEP_TARGET",
            "reasonCode": "SUCCESS_CONFIRMATION_REQUIRED",
            "reasonArguments": {
                "actualReps": 5.0,
                "actualWeightKg": 100.0,
                "countedWorkSets": 3.0,
                "failureMarkedSets": 0.0,
                "ignoredBackoffSets": 0.0,
                "ignoredDropSets": 1.0,
                "ignoredExtraSets": 0.0,
                "ignoredWarmupSets": 1.0,
                "maxWeightKg": 100.0,
                "minWeightKg": 100.0,
                "requiredSuccesses": 2.0,
                "successfulSessions": 1.0,
                "targetReps": 5.0
            },
            "countedSetIds": [
                2,
                3,
                4
            ],
            "streakEffect": "RESET",
            "suggestedTarget": null,
            "status": "INFORMATIONAL",
            "wasEdited": false,
            "finalTarget": null,
            "createdAtEpochMillis": 1788249900000,
            "decidedAtEpochMillis": null
        },
        {
            "id": 2,
            "sourceSessionId": 1,
            "sourceTargetSnapshotId": 2,
            "planId": 1,
            "exerciseId": 3,
            "orderIndex": 1,
            "supersetGroupId": null,
            "sourceTarget": {
                "sets": 3,
                "reps": 8,
                "weightKg": 60.0
            },
            "sourceProgression": {
                "scheme": "DOUBLE",
                "incrementValue": 2.5,
                "incrementUnit": "METRIC",
                "incrementKg": 2.5,
                "minReps": 8,
                "maxReps": 12,
                "targetTotalReps": null,
                "targetRpe": null,
                "rpeTolerance": null,
                "stallThreshold": 2,
                "backoffPercent": 10.0,
                "ruleRevision": 2,
                "successThreshold": 1
            },
            "outcomeType": "PROPOSE_CHANGE",
            "reasonCode": "LOAD_ADVANCED",
            "reasonArguments": {
                "actualReps": 12.0,
                "actualWeightKg": 60.0,
                "countedWorkSets": 3.0,
                "failureMarkedSets": 0.0,
                "ignoredBackoffSets": 0.0,
                "ignoredDropSets": 0.0,
                "ignoredExtraSets": 0.0,
                "ignoredWarmupSets": 0.0,
                "maxWeightKg": 60.0,
                "minWeightKg": 60.0,
                "stepOriginalValue": 2.5,
                "targetReps": 8.0
            },
            "countedSetIds": [
                6,
                7,
                8
            ],
            "streakEffect": "RESET",
            "suggestedTarget": {
                "sets": 3,
                "reps": 8,
                "weightKg": 62.5
            },
            "status": "ACCEPTED",
            "wasEdited": false,
            "finalTarget": {
                "sets": 3,
                "reps": 8,
                "weightKg": 62.5
            },
            "createdAtEpochMillis": 1788249900000,
            "decidedAtEpochMillis": 1788285600000
        },
        {
            "id": 3,
            "sourceSessionId": 1,
            "sourceTargetSnapshotId": 3,
            "planId": 1,
            "exerciseId": 80,
            "orderIndex": 2,
            "supersetGroupId": 1,
            "sourceTarget": {
                "sets": 3,
                "reps": 10,
                "weightKg": 20.0
            },
            "sourceProgression": {
                "scheme": "TOTAL_REPS",
                "incrementValue": 5.0,
                "incrementUnit": "IMPERIAL",
                "incrementKg": 2.267961850050177,
                "minReps": null,
                "maxReps": null,
                "targetTotalReps": 36,
                "targetRpe": null,
                "rpeTolerance": null,
                "stallThreshold": 3,
                "backoffPercent": 7.5,
                "ruleRevision": 2,
                "successThreshold": 1
            },
            "outcomeType": "PROPOSE_CHANGE",
            "reasonCode": "TOTAL_REPS_COMPLETED",
            "reasonArguments": {
                "achievedTotalReps": 37.0,
                "actualWeightKg": 20.0,
                "countedWorkSets": 3.0,
                "failureMarkedSets": 0.0,
                "ignoredBackoffSets": 0.0,
                "ignoredDropSets": 0.0,
                "ignoredExtraSets": 0.0,
                "ignoredWarmupSets": 0.0,
                "maxWeightKg": 20.0,
                "minWeightKg": 20.0,
                "stepOriginalValue": 5.0,
                "targetTotalReps": 36.0
            },
            "countedSetIds": [
                9,
                11,
                13
            ],
            "streakEffect": "RESET",
            "suggestedTarget": {
                "sets": 3,
                "reps": 10,
                "weightKg": 22.267961850050177
            },
            "status": "ACCEPTED",
            "wasEdited": true,
            "finalTarget": {
                "sets": 3,
                "reps": 11,
                "weightKg": 22.267961850050177
            },
            "createdAtEpochMillis": 1788249900000,
            "decidedAtEpochMillis": 1788285600000
        },
        {
            "id": 4,
            "sourceSessionId": 1,
            "sourceTargetSnapshotId": 4,
            "planId": 1,
            "exerciseId": 47,
            "orderIndex": 3,
            "supersetGroupId": 1,
            "sourceTarget": {
                "sets": 3,
                "reps": 12,
                "weightKg": 10.0
            },
            "sourceProgression": {
                "scheme": "RPE_RIR",
                "incrementValue": 1.0,
                "incrementUnit": "METRIC",
                "incrementKg": 1.0,
                "minReps": null,
                "maxReps": null,
                "targetTotalReps": null,
                "targetRpe": 8.0,
                "rpeTolerance": 0.5,
                "stallThreshold": 2,
                "backoffPercent": 10.0,
                "ruleRevision": 2,
                "successThreshold": 1
            },
            "outcomeType": "PROPOSE_CHANGE",
            "reasonCode": "RPE_WITHIN_TARGET",
            "reasonArguments": {
                "actualWeightKg": 10.0,
                "countedWorkSets": 3.0,
                "failureMarkedSets": 0.0,
                "highestRpe": 8.5,
                "ignoredBackoffSets": 0.0,
                "ignoredDropSets": 0.0,
                "ignoredExtraSets": 0.0,
                "ignoredWarmupSets": 0.0,
                "maxWeightKg": 10.0,
                "minWeightKg": 10.0,
                "stepOriginalValue": 1.0,
                "targetRpe": 8.0,
                "tolerance": 0.5
            },
            "countedSetIds": [
                10,
                12,
                14
            ],
            "streakEffect": "RESET",
            "suggestedTarget": {
                "sets": 3,
                "reps": 12,
                "weightKg": 11.0
            },
            "status": "REJECTED",
            "wasEdited": false,
            "finalTarget": null,
            "createdAtEpochMillis": 1788249900000,
            "decidedAtEpochMillis": 1788285600000
        },
        {
            "id": 5,
            "sourceSessionId": 2,
            "sourceTargetSnapshotId": 6,
            "planId": 2,
            "exerciseId": 13,
            "orderIndex": 0,
            "supersetGroupId": null,
            "sourceTarget": {
                "sets": 3,
                "reps": 8,
                "weightKg": 70.0
            },
            "sourceProgression": {
                "scheme": "LINEAR",
                "incrementValue": 2.5,
                "incrementUnit": "METRIC",
                "incrementKg": 2.5,
                "minReps": null,
                "maxReps": null,
                "targetTotalReps": null,
                "targetRpe": null,
                "rpeTolerance": null,
                "stallThreshold": 2,
                "backoffPercent": 10.0,
                "ruleRevision": 1,
                "successThreshold": 1
            },
            "outcomeType": "KEEP_TARGET",
            "reasonCode": "REPEAT_TARGET",
            "reasonArguments": {
                "actualReps": 7.0,
                "actualWeightKg": 70.0,
                "targetReps": 8.0
            },
            "countedSetIds": [
                19,
                20,
                21
            ],
            "streakEffect": "INCREMENT",
            "suggestedTarget": null,
            "status": "INFORMATIONAL",
            "wasEdited": false,
            "finalTarget": null,
            "createdAtEpochMillis": 1788421800000,
            "decidedAtEpochMillis": null
        },
        {
            "id": 6,
            "sourceSessionId": 2,
            "sourceTargetSnapshotId": 8,
            "planId": 2,
            "exerciseId": 81,
            "orderIndex": 2,
            "supersetGroupId": null,
            "sourceTarget": {
                "sets": 3,
                "reps": 15,
                "weightKg": 30.0
            },
            "sourceProgression": {
                "scheme": "LINEAR",
                "incrementValue": 2.5,
                "incrementUnit": "METRIC",
                "incrementKg": 2.5,
                "minReps": null,
                "maxReps": null,
                "targetTotalReps": null,
                "targetRpe": null,
                "rpeTolerance": null,
                "stallThreshold": 2,
                "backoffPercent": 10.0,
                "ruleRevision": 3,
                "successThreshold": 1
            },
            "outcomeType": "INSUFFICIENT_DATA",
            "reasonCode": "RULE_REVISION_UNSUPPORTED",
            "reasonArguments": {},
            "countedSetIds": [
                26,
                27,
                28
            ],
            "streakEffect": "IGNORE",
            "suggestedTarget": null,
            "status": "INFORMATIONAL",
            "wasEdited": false,
            "finalTarget": null,
            "createdAtEpochMillis": 1788421800000,
            "decidedAtEpochMillis": null
        },
        {
            "id": 7,
            "sourceSessionId": 3,
            "sourceTargetSnapshotId": 9,
            "planId": 1,
            "exerciseId": 1,
            "orderIndex": 0,
            "supersetGroupId": null,
            "sourceTarget": {
                "sets": 3,
                "reps": 5,
                "weightKg": 100.0
            },
            "sourceProgression": {
                "scheme": "LINEAR",
                "incrementValue": 2.5,
                "incrementUnit": "METRIC",
                "incrementKg": 2.5,
                "minReps": null,
                "maxReps": null,
                "targetTotalReps": null,
                "targetRpe": null,
                "rpeTolerance": null,
                "stallThreshold": 2,
                "backoffPercent": 10.0,
                "ruleRevision": 2,
                "successThreshold": 2
            },
            "outcomeType": "PROPOSE_CHANGE",
            "reasonCode": "LOAD_ADVANCED",
            "reasonArguments": {
                "actualReps": 5.0,
                "actualWeightKg": 100.0,
                "countedWorkSets": 3.0,
                "failureMarkedSets": 0.0,
                "ignoredBackoffSets": 0.0,
                "ignoredDropSets": 0.0,
                "ignoredExtraSets": 0.0,
                "ignoredWarmupSets": 0.0,
                "maxWeightKg": 100.0,
                "minWeightKg": 100.0,
                "requiredSuccesses": 2.0,
                "stepOriginalValue": 2.5,
                "successfulSessions": 2.0,
                "targetReps": 5.0
            },
            "countedSetIds": [
                29,
                30,
                31
            ],
            "streakEffect": "RESET",
            "suggestedTarget": {
                "sets": 3,
                "reps": 5,
                "weightKg": 102.5
            },
            "status": "STALE",
            "wasEdited": false,
            "finalTarget": null,
            "createdAtEpochMillis": 1788594300000,
            "decidedAtEpochMillis": 1788634800000
        },
        {
            "id": 8,
            "sourceSessionId": 3,
            "sourceTargetSnapshotId": 10,
            "planId": 1,
            "exerciseId": 3,
            "orderIndex": 1,
            "supersetGroupId": null,
            "sourceTarget": {
                "sets": 3,
                "reps": 8,
                "weightKg": 62.5
            },
            "sourceProgression": {
                "scheme": "DOUBLE",
                "incrementValue": 2.5,
                "incrementUnit": "METRIC",
                "incrementKg": 2.5,
                "minReps": 8,
                "maxReps": 12,
                "targetTotalReps": null,
                "targetRpe": null,
                "rpeTolerance": null,
                "stallThreshold": 2,
                "backoffPercent": 10.0,
                "ruleRevision": 2,
                "successThreshold": 1
            },
            "outcomeType": "KEEP_TARGET",
            "reasonCode": "REPEAT_TARGET",
            "reasonArguments": {
                "actualReps": 7.0,
                "actualWeightKg": 62.5,
                "countedWorkSets": 3.0,
                "failedSessions": 1.0,
                "failureMarkedSets": 0.0,
                "failureThreshold": 2.0,
                "ignoredBackoffSets": 0.0,
                "ignoredDropSets": 0.0,
                "ignoredExtraSets": 0.0,
                "ignoredWarmupSets": 0.0,
                "maxWeightKg": 62.5,
                "minWeightKg": 62.5,
                "targetReps": 8.0
            },
            "countedSetIds": [
                32,
                33,
                34
            ],
            "streakEffect": "INCREMENT",
            "suggestedTarget": null,
            "status": "INFORMATIONAL",
            "wasEdited": false,
            "finalTarget": null,
            "createdAtEpochMillis": 1788594300000,
            "decidedAtEpochMillis": null
        },
        {
            "id": 9,
            "sourceSessionId": 3,
            "sourceTargetSnapshotId": 11,
            "planId": 1,
            "exerciseId": 80,
            "orderIndex": 2,
            "supersetGroupId": 1,
            "sourceTarget": {
                "sets": 3,
                "reps": 11,
                "weightKg": 22.267961850050177
            },
            "sourceProgression": {
                "scheme": "TOTAL_REPS",
                "incrementValue": 5.0,
                "incrementUnit": "IMPERIAL",
                "incrementKg": 2.267961850050177,
                "minReps": null,
                "maxReps": null,
                "targetTotalReps": 36,
                "targetRpe": null,
                "rpeTolerance": null,
                "stallThreshold": 3,
                "backoffPercent": 7.5,
                "ruleRevision": 2,
                "successThreshold": 1
            },
            "outcomeType": "INSUFFICIENT_DATA",
            "reasonCode": "TOO_FEW_WORK_SETS",
            "reasonArguments": {
                "actualWorkSets": 1.0,
                "countedWorkSets": 1.0,
                "failureMarkedSets": 0.0,
                "ignoredBackoffSets": 0.0,
                "ignoredDropSets": 0.0,
                "ignoredExtraSets": 0.0,
                "ignoredWarmupSets": 0.0,
                "targetSets": 3.0
            },
            "countedSetIds": [
                35
            ],
            "streakEffect": "IGNORE",
            "suggestedTarget": null,
            "status": "INFORMATIONAL",
            "wasEdited": false,
            "finalTarget": null,
            "createdAtEpochMillis": 1788594300000,
            "decidedAtEpochMillis": null
        },
        {
            "id": 10,
            "sourceSessionId": 3,
            "sourceTargetSnapshotId": 12,
            "planId": 1,
            "exerciseId": 47,
            "orderIndex": 3,
            "supersetGroupId": 1,
            "sourceTarget": {
                "sets": 3,
                "reps": 12,
                "weightKg": 10.0
            },
            "sourceProgression": {
                "scheme": "RPE_RIR",
                "incrementValue": 1.0,
                "incrementUnit": "METRIC",
                "incrementKg": 1.0,
                "minReps": null,
                "maxReps": null,
                "targetTotalReps": null,
                "targetRpe": 8.0,
                "rpeTolerance": 0.5,
                "stallThreshold": 2,
                "backoffPercent": 10.0,
                "ruleRevision": 2,
                "successThreshold": 1
            },
            "outcomeType": "INSUFFICIENT_DATA",
            "reasonCode": "TOO_FEW_WORK_SETS",
            "reasonArguments": {
                "actualWorkSets": 0.0,
                "countedWorkSets": 0.0,
                "failureMarkedSets": 0.0,
                "ignoredBackoffSets": 0.0,
                "ignoredDropSets": 0.0,
                "ignoredExtraSets": 0.0,
                "ignoredWarmupSets": 0.0,
                "targetSets": 3.0
            },
            "countedSetIds": [],
            "streakEffect": "IGNORE",
            "suggestedTarget": null,
            "status": "INFORMATIONAL",
            "wasEdited": false,
            "finalTarget": null,
            "createdAtEpochMillis": 1788594300000,
            "decidedAtEpochMillis": null
        },
        {
            "id": 11,
            "sourceSessionId": 5,
            "sourceTargetSnapshotId": 15,
            "planId": 1,
            "exerciseId": 1,
            "orderIndex": 0,
            "supersetGroupId": null,
            "sourceTarget": {
                "sets": 3,
                "reps": 5,
                "weightKg": 100.0
            },
            "sourceProgression": {
                "scheme": "LINEAR",
                "incrementValue": 2.5,
                "incrementUnit": "METRIC",
                "incrementKg": 2.5,
                "minReps": null,
                "maxReps": null,
                "targetTotalReps": null,
                "targetRpe": null,
                "rpeTolerance": null,
                "stallThreshold": 2,
                "backoffPercent": 10.0,
                "ruleRevision": 2,
                "successThreshold": 2
            },
            "outcomeType": "PROPOSE_CHANGE",
            "reasonCode": "LOAD_ADVANCED",
            "reasonArguments": {
                "actualReps": 5.0,
                "actualWeightKg": 100.0,
                "countedWorkSets": 3.0,
                "failureMarkedSets": 0.0,
                "ignoredBackoffSets": 0.0,
                "ignoredDropSets": 0.0,
                "ignoredExtraSets": 0.0,
                "ignoredWarmupSets": 0.0,
                "maxWeightKg": 100.0,
                "minWeightKg": 100.0,
                "requiredSuccesses": 2.0,
                "stepOriginalValue": 2.5,
                "successfulSessions": 2.0,
                "targetReps": 5.0
            },
            "countedSetIds": [
                40,
                41,
                42
            ],
            "streakEffect": "RESET",
            "suggestedTarget": {
                "sets": 3,
                "reps": 5,
                "weightKg": 102.5
            },
            "status": "PENDING",
            "wasEdited": false,
            "finalTarget": null,
            "createdAtEpochMillis": 1788939000000,
            "decidedAtEpochMillis": null
        },
        {
            "id": 12,
            "sourceSessionId": 5,
            "sourceTargetSnapshotId": 16,
            "planId": 1,
            "exerciseId": 3,
            "orderIndex": 1,
            "supersetGroupId": null,
            "sourceTarget": {
                "sets": 3,
                "reps": 8,
                "weightKg": 62.5
            },
            "sourceProgression": {
                "scheme": "DOUBLE",
                "incrementValue": 2.5,
                "incrementUnit": "METRIC",
                "incrementKg": 2.5,
                "minReps": 8,
                "maxReps": 12,
                "targetTotalReps": null,
                "targetRpe": null,
                "rpeTolerance": null,
                "stallThreshold": 2,
                "backoffPercent": 10.0,
                "ruleRevision": 2,
                "successThreshold": 1
            },
            "outcomeType": "INSUFFICIENT_DATA",
            "reasonCode": "TOO_FEW_WORK_SETS",
            "reasonArguments": {
                "actualWorkSets": 0.0,
                "countedWorkSets": 0.0,
                "failureMarkedSets": 0.0,
                "ignoredBackoffSets": 0.0,
                "ignoredDropSets": 0.0,
                "ignoredExtraSets": 0.0,
                "ignoredWarmupSets": 0.0,
                "targetSets": 3.0
            },
            "countedSetIds": [],
            "streakEffect": "IGNORE",
            "suggestedTarget": null,
            "status": "INFORMATIONAL",
            "wasEdited": false,
            "finalTarget": null,
            "createdAtEpochMillis": 1788939000000,
            "decidedAtEpochMillis": null
        },
        {
            "id": 13,
            "sourceSessionId": 5,
            "sourceTargetSnapshotId": 17,
            "planId": 1,
            "exerciseId": 80,
            "orderIndex": 2,
            "supersetGroupId": 1,
            "sourceTarget": {
                "sets": 3,
                "reps": 11,
                "weightKg": 22.267961850050177
            },
            "sourceProgression": {
                "scheme": "TOTAL_REPS",
                "incrementValue": 5.0,
                "incrementUnit": "IMPERIAL",
                "incrementKg": 2.267961850050177,
                "minReps": null,
                "maxReps": null,
                "targetTotalReps": 36,
                "targetRpe": null,
                "rpeTolerance": null,
                "stallThreshold": 3,
                "backoffPercent": 7.5,
                "ruleRevision": 2,
                "successThreshold": 1
            },
            "outcomeType": "INSUFFICIENT_DATA",
            "reasonCode": "TOO_FEW_WORK_SETS",
            "reasonArguments": {
                "actualWorkSets": 0.0,
                "countedWorkSets": 0.0,
                "failureMarkedSets": 0.0,
                "ignoredBackoffSets": 0.0,
                "ignoredDropSets": 0.0,
                "ignoredExtraSets": 0.0,
                "ignoredWarmupSets": 0.0,
                "targetSets": 3.0
            },
            "countedSetIds": [],
            "streakEffect": "IGNORE",
            "suggestedTarget": null,
            "status": "INFORMATIONAL",
            "wasEdited": false,
            "finalTarget": null,
            "createdAtEpochMillis": 1788939000000,
            "decidedAtEpochMillis": null
        },
        {
            "id": 14,
            "sourceSessionId": 5,
            "sourceTargetSnapshotId": 18,
            "planId": 1,
            "exerciseId": 47,
            "orderIndex": 3,
            "supersetGroupId": 1,
            "sourceTarget": {
                "sets": 3,
                "reps": 12,
                "weightKg": 10.0
            },
            "sourceProgression": {
                "scheme": "RPE_RIR",
                "incrementValue": 1.0,
                "incrementUnit": "METRIC",
                "incrementKg": 1.0,
                "minReps": null,
                "maxReps": null,
                "targetTotalReps": null,
                "targetRpe": 8.0,
                "rpeTolerance": 0.5,
                "stallThreshold": 2,
                "backoffPercent": 10.0,
                "ruleRevision": 2,
                "successThreshold": 1
            },
            "outcomeType": "INSUFFICIENT_DATA",
            "reasonCode": "TOO_FEW_WORK_SETS",
            "reasonArguments": {
                "actualWorkSets": 0.0,
                "countedWorkSets": 0.0,
                "failureMarkedSets": 0.0,
                "ignoredBackoffSets": 0.0,
                "ignoredDropSets": 0.0,
                "ignoredExtraSets": 0.0,
                "ignoredWarmupSets": 0.0,
                "targetSets": 3.0
            },
            "countedSetIds": [],
            "streakEffect": "IGNORE",
            "suggestedTarget": null,
            "status": "INFORMATIONAL",
            "wasEdited": false,
            "finalTarget": null,
            "createdAtEpochMillis": 1788939000000,
            "decidedAtEpochMillis": null
        }
    ],
    "readinessData": {
        "formatVersion": 1,
        "schemaVersion": 1,
        "checkIns": [
            {
                "localDate": "2026-09-01",
                "sleepQuality": 4,
                "energy": 3,
                "stress": 2,
                "muscleSoreness": {
                    "BRUST": 2,
                    "SCHULTERN": 3
                },
                "recordedAtEpochMillis": 1788244200000,
                "updatedAtEpochMillis": null
            },
            {
                "localDate": "2026-09-03",
                "sleepQuality": 2,
                "energy": null,
                "stress": null,
                "muscleSoreness": {},
                "recordedAtEpochMillis": 1788417900000,
                "updatedAtEpochMillis": null
            },
            {
                "localDate": "2026-09-05",
                "sleepQuality": null,
                "energy": 4,
                "stress": 3,
                "muscleSoreness": {
                    "BEINE": 4,
                    "GESAESS": 2
                },
                "recordedAtEpochMillis": 1788589800000,
                "updatedAtEpochMillis": 1788590400000
            }
        ],
        "setIntentions": [
            {
                "setId": 17,
                "intention": "PLANNED_FAILURE",
                "recordedAtEpochMillis": 1788248040000,
                "note": ""
            },
            {
                "setId": 18,
                "intention": "UNEXPECTED_TARGET_MISS",
                "recordedAtEpochMillis": 1788248160000,
                "note": "Wollte 15, Griff rutschte"
            },
            {
                "setId": 21,
                "intention": "UNEXPECTED_TARGET_MISS",
                "recordedAtEpochMillis": 1788419160000,
                "note": ""
            },
            {
                "setId": 25,
                "intention": "PLANNED_FAILURE",
                "recordedAtEpochMillis": 1788419640000,
                "note": ""
            },
            {
                "setId": 45,
                "intention": "UNEXPECTED_TARGET_MISS",
                "recordedAtEpochMillis": 1789063560000,
                "note": ""
            }
        ]
    }
}"""

    val IOS_EXPORT_JSON: String = """{"formatVersion":1,"schemaVersion":15,"appVersion":"0.1.0","exportedAtEpochMillis":1789128000000,"exercises":[{"id":1,"name":"Bankdrücken (Langhantel)","primaryMuscleGroup":"BRUST","secondaryMuscleGroups":"TRIZEPS,SCHULTERN","category":"LANGHANTEL","isCustom":false,"notes":"","isArchived":false},{"id":2,"name":"Bankdrücken (Kurzhantel)","primaryMuscleGroup":"BRUST","secondaryMuscleGroups":"TRIZEPS,SCHULTERN","category":"KURZHANTEL","isCustom":false,"notes":"","isArchived":false},{"id":3,"name":"Schrägbankdrücken (Langhantel)","primaryMuscleGroup":"BRUST","secondaryMuscleGroups":"TRIZEPS,SCHULTERN","category":"LANGHANTEL","isCustom":false,"notes":"","isArchived":false},{"id":4,"name":"Schrägbankdrücken (Kurzhantel)","primaryMuscleGroup":"BRUST","secondaryMuscleGroups":"TRIZEPS,SCHULTERN","category":"KURZHANTEL","isCustom":false,"notes":"","isArchived":false},{"id":5,"name":"Negativbankdrücken (Langhantel)","primaryMuscleGroup":"BRUST","secondaryMuscleGroups":"TRIZEPS","category":"LANGHANTEL","isCustom":false,"notes":"","isArchived":false},{"id":6,"name":"Fliegende (Kurzhantel)","primaryMuscleGroup":"BRUST","secondaryMuscleGroups":"","category":"KURZHANTEL","isCustom":false,"notes":"","isArchived":false},{"id":7,"name":"Cable Fly","primaryMuscleGroup":"BRUST","secondaryMuscleGroups":"","category":"KABEL","isCustom":false,"notes":"","isArchived":false},{"id":8,"name":"Cable Fly obere Brust","primaryMuscleGroup":"BRUST","secondaryMuscleGroups":"SCHULTERN","category":"KABEL","isCustom":false,"notes":"","isArchived":false},{"id":9,"name":"Butterfly Maschine","primaryMuscleGroup":"BRUST","secondaryMuscleGroups":"","category":"MASCHINE","isCustom":false,"notes":"","isArchived":false},{"id":10,"name":"Brustpresse (Maschine)","primaryMuscleGroup":"BRUST","secondaryMuscleGroups":"TRIZEPS,SCHULTERN","category":"MASCHINE","isCustom":false,"notes":"","isArchived":false},{"id":11,"name":"Liegestütze","primaryMuscleGroup":"BRUST","secondaryMuscleGroups":"TRIZEPS,SCHULTERN,CORE","category":"EIGENGEWICHT","isCustom":false,"notes":"","isArchived":false},{"id":12,"name":"Dips (Brust)","primaryMuscleGroup":"BRUST","secondaryMuscleGroups":"TRIZEPS,SCHULTERN","category":"EIGENGEWICHT","isCustom":false,"notes":"","isArchived":false},{"id":13,"name":"Langhantelrudern","primaryMuscleGroup":"RUECKEN","secondaryMuscleGroups":"BIZEPS","category":"LANGHANTEL","isCustom":false,"notes":"","isArchived":false},{"id":14,"name":"Kurzhantelrudern","primaryMuscleGroup":"RUECKEN","secondaryMuscleGroups":"BIZEPS","category":"KURZHANTEL","isCustom":false,"notes":"","isArchived":false},{"id":15,"name":"Klimmzug","primaryMuscleGroup":"RUECKEN","secondaryMuscleGroups":"BIZEPS","category":"EIGENGEWICHT","isCustom":false,"notes":"","isArchived":false},{"id":16,"name":"Klimmzug (Untergriff)","primaryMuscleGroup":"RUECKEN","secondaryMuscleGroups":"BIZEPS","category":"EIGENGEWICHT","isCustom":false,"notes":"","isArchived":false},{"id":17,"name":"Latzug breit","primaryMuscleGroup":"RUECKEN","secondaryMuscleGroups":"BIZEPS","category":"KABEL","isCustom":false,"notes":"","isArchived":false},{"id":18,"name":"Latzug eng","primaryMuscleGroup":"RUECKEN","secondaryMuscleGroups":"BIZEPS","category":"KABEL","isCustom":false,"notes":"","isArchived":false},{"id":19,"name":"Kabelrudern sitzend","primaryMuscleGroup":"RUECKEN","secondaryMuscleGroups":"BIZEPS","category":"KABEL","isCustom":false,"notes":"","isArchived":false},{"id":20,"name":"T-Bar Rudern","primaryMuscleGroup":"RUECKEN","secondaryMuscleGroups":"BIZEPS","category":"LANGHANTEL","isCustom":false,"notes":"","isArchived":false},{"id":21,"name":"Rudermaschine","primaryMuscleGroup":"RUECKEN","secondaryMuscleGroups":"BIZEPS","category":"MASCHINE","isCustom":false,"notes":"","isArchived":false},{"id":22,"name":"Kreuzheben","primaryMuscleGroup":"RUECKEN","secondaryMuscleGroups":"BEINE,GESAESS,CORE","category":"LANGHANTEL","isCustom":false,"notes":"","isArchived":false},{"id":23,"name":"Hyperextension","primaryMuscleGroup":"RUECKEN","secondaryMuscleGroups":"GESAESS","category":"EIGENGEWICHT","isCustom":false,"notes":"","isArchived":false},{"id":24,"name":"Face Pulls","primaryMuscleGroup":"RUECKEN","secondaryMuscleGroups":"SCHULTERN","category":"KABEL","isCustom":false,"notes":"","isArchived":false},{"id":25,"name":"Einarmiges Kabelziehen","primaryMuscleGroup":"RUECKEN","secondaryMuscleGroups":"BIZEPS","category":"KABEL","isCustom":false,"notes":"","isArchived":false},{"id":26,"name":"Kniebeuge (Langhantel)","primaryMuscleGroup":"BEINE","secondaryMuscleGroups":"GESAESS,CORE","category":"LANGHANTEL","isCustom":false,"notes":"","isArchived":false},{"id":27,"name":"Frontkniebeuge","primaryMuscleGroup":"BEINE","secondaryMuscleGroups":"CORE,GESAESS","category":"LANGHANTEL","isCustom":false,"notes":"","isArchived":false},{"id":28,"name":"Rumänisches Kreuzheben","primaryMuscleGroup":"BEINE","secondaryMuscleGroups":"GESAESS,RUECKEN","category":"LANGHANTEL","isCustom":false,"notes":"","isArchived":false},{"id":29,"name":"Rumänisches Kreuzheben (Kurzhantel)","primaryMuscleGroup":"BEINE","secondaryMuscleGroups":"GESAESS,RUECKEN","category":"KURZHANTEL","isCustom":false,"notes":"","isArchived":false},{"id":30,"name":"Beinpresse","primaryMuscleGroup":"BEINE","secondaryMuscleGroups":"GESAESS","category":"MASCHINE","isCustom":false,"notes":"","isArchived":false},{"id":31,"name":"Hackenschmidt","primaryMuscleGroup":"BEINE","secondaryMuscleGroups":"GESAESS","category":"MASCHINE","isCustom":false,"notes":"","isArchived":false},{"id":32,"name":"Ausfallschritte (Langhantel)","primaryMuscleGroup":"BEINE","secondaryMuscleGroups":"GESAESS","category":"LANGHANTEL","isCustom":false,"notes":"","isArchived":false},{"id":33,"name":"Ausfallschritte (Kurzhantel)","primaryMuscleGroup":"BEINE","secondaryMuscleGroups":"GESAESS","category":"KURZHANTEL","isCustom":false,"notes":"","isArchived":false},{"id":34,"name":"Bulgarische Kniebeuge","primaryMuscleGroup":"BEINE","secondaryMuscleGroups":"GESAESS","category":"KURZHANTEL","isCustom":false,"notes":"","isArchived":false},{"id":35,"name":"Beinstrecker","primaryMuscleGroup":"BEINE","secondaryMuscleGroups":"","category":"MASCHINE","isCustom":false,"notes":"","isArchived":false},{"id":36,"name":"Beinbeuger liegend","primaryMuscleGroup":"BEINE","secondaryMuscleGroups":"","category":"MASCHINE","isCustom":false,"notes":"","isArchived":false},{"id":37,"name":"Beinbeuger sitzend","primaryMuscleGroup":"BEINE","secondaryMuscleGroups":"","category":"MASCHINE","isCustom":false,"notes":"","isArchived":false},{"id":38,"name":"Goblet Squat","primaryMuscleGroup":"BEINE","secondaryMuscleGroups":"GESAESS,CORE","category":"KURZHANTEL","isCustom":false,"notes":"","isArchived":false},{"id":39,"name":"Sumo Kreuzheben","primaryMuscleGroup":"BEINE","secondaryMuscleGroups":"GESAESS,RUECKEN","category":"LANGHANTEL","isCustom":false,"notes":"","isArchived":false},{"id":40,"name":"Hip Thrust (Langhantel)","primaryMuscleGroup":"GESAESS","secondaryMuscleGroups":"BEINE","category":"LANGHANTEL","isCustom":false,"notes":"","isArchived":false},{"id":41,"name":"Hip Thrust (Maschine)","primaryMuscleGroup":"GESAESS","secondaryMuscleGroups":"BEINE","category":"MASCHINE","isCustom":false,"notes":"","isArchived":false},{"id":42,"name":"Glute Kickback (Kabel)","primaryMuscleGroup":"GESAESS","secondaryMuscleGroups":"","category":"KABEL","isCustom":false,"notes":"","isArchived":false},{"id":43,"name":"Abduktion (Maschine)","primaryMuscleGroup":"GESAESS","secondaryMuscleGroups":"","category":"MASCHINE","isCustom":false,"notes":"","isArchived":false},{"id":44,"name":"Schulterdrücken (Langhantel)","primaryMuscleGroup":"SCHULTERN","secondaryMuscleGroups":"TRIZEPS","category":"LANGHANTEL","isCustom":false,"notes":"","isArchived":false},{"id":45,"name":"Schulterdrücken (Kurzhantel)","primaryMuscleGroup":"SCHULTERN","secondaryMuscleGroups":"TRIZEPS","category":"KURZHANTEL","isCustom":false,"notes":"","isArchived":false},{"id":46,"name":"Schulterdrücken (Maschine)","primaryMuscleGroup":"SCHULTERN","secondaryMuscleGroups":"TRIZEPS","category":"MASCHINE","isCustom":false,"notes":"","isArchived":false},{"id":47,"name":"Seitheben (Kurzhantel)","primaryMuscleGroup":"SCHULTERN","secondaryMuscleGroups":"","category":"KURZHANTEL","isCustom":false,"notes":"","isArchived":false},{"id":48,"name":"Seitheben (Kabel)","primaryMuscleGroup":"SCHULTERN","secondaryMuscleGroups":"","category":"KABEL","isCustom":false,"notes":"","isArchived":false},{"id":49,"name":"Frontheben","primaryMuscleGroup":"SCHULTERN","secondaryMuscleGroups":"BRUST","category":"KURZHANTEL","isCustom":false,"notes":"","isArchived":false},{"id":50,"name":"Aufrechtes Rudern","primaryMuscleGroup":"SCHULTERN","secondaryMuscleGroups":"BIZEPS","category":"LANGHANTEL","isCustom":false,"notes":"","isArchived":false},{"id":51,"name":"Arnold Press","primaryMuscleGroup":"SCHULTERN","secondaryMuscleGroups":"TRIZEPS","category":"KURZHANTEL","isCustom":false,"notes":"","isArchived":false},{"id":52,"name":"Reverse Fly (Kurzhantel)","primaryMuscleGroup":"SCHULTERN","secondaryMuscleGroups":"RUECKEN","category":"KURZHANTEL","isCustom":false,"notes":"","isArchived":false},{"id":53,"name":"Reverse Fly (Maschine)","primaryMuscleGroup":"SCHULTERN","secondaryMuscleGroups":"RUECKEN","category":"MASCHINE","isCustom":false,"notes":"","isArchived":false},{"id":54,"name":"Langhantelcurls","primaryMuscleGroup":"BIZEPS","secondaryMuscleGroups":"UNTERARME","category":"LANGHANTEL","isCustom":false,"notes":"","isArchived":false},{"id":55,"name":"SZ-Curls","primaryMuscleGroup":"BIZEPS","secondaryMuscleGroups":"UNTERARME","category":"LANGHANTEL","isCustom":false,"notes":"","isArchived":false},{"id":56,"name":"Kurzhantelcurls","primaryMuscleGroup":"BIZEPS","secondaryMuscleGroups":"UNTERARME","category":"KURZHANTEL","isCustom":false,"notes":"","isArchived":false},{"id":57,"name":"Hammercurls","primaryMuscleGroup":"BIZEPS","secondaryMuscleGroups":"UNTERARME","category":"KURZHANTEL","isCustom":false,"notes":"","isArchived":false},{"id":58,"name":"Konzentrationscurls","primaryMuscleGroup":"BIZEPS","secondaryMuscleGroups":"","category":"KURZHANTEL","isCustom":false,"notes":"","isArchived":false},{"id":59,"name":"Kabelcurls","primaryMuscleGroup":"BIZEPS","secondaryMuscleGroups":"UNTERARME","category":"KABEL","isCustom":false,"notes":"","isArchived":false},{"id":60,"name":"Schrägbankcurls","primaryMuscleGroup":"BIZEPS","secondaryMuscleGroups":"","category":"KURZHANTEL","isCustom":false,"notes":"","isArchived":false},{"id":61,"name":"Trizepsdrücken am Kabel","primaryMuscleGroup":"TRIZEPS","secondaryMuscleGroups":"","category":"KABEL","isCustom":false,"notes":"","isArchived":false},{"id":62,"name":"Trizepsdrücken Seil","primaryMuscleGroup":"TRIZEPS","secondaryMuscleGroups":"","category":"KABEL","isCustom":false,"notes":"","isArchived":false},{"id":63,"name":"French Press (SZ-Stange)","primaryMuscleGroup":"TRIZEPS","secondaryMuscleGroups":"","category":"LANGHANTEL","isCustom":false,"notes":"","isArchived":false},{"id":64,"name":"Stirndrücken (Kurzhantel)","primaryMuscleGroup":"TRIZEPS","secondaryMuscleGroups":"","category":"KURZHANTEL","isCustom":false,"notes":"","isArchived":false},{"id":65,"name":"Dips (Trizeps)","primaryMuscleGroup":"TRIZEPS","secondaryMuscleGroups":"BRUST,SCHULTERN","category":"EIGENGEWICHT","isCustom":false,"notes":"","isArchived":false},{"id":66,"name":"Kickbacks (Kurzhantel)","primaryMuscleGroup":"TRIZEPS","secondaryMuscleGroups":"","category":"KURZHANTEL","isCustom":false,"notes":"","isArchived":false},{"id":67,"name":"Enges Bankdrücken","primaryMuscleGroup":"TRIZEPS","secondaryMuscleGroups":"BRUST,SCHULTERN","category":"LANGHANTEL","isCustom":false,"notes":"","isArchived":false},{"id":68,"name":"Plank","primaryMuscleGroup":"CORE","secondaryMuscleGroups":"","category":"EIGENGEWICHT","isCustom":false,"notes":"","isArchived":false},{"id":69,"name":"Cable Crunch","primaryMuscleGroup":"CORE","secondaryMuscleGroups":"","category":"KABEL","isCustom":false,"notes":"","isArchived":false},{"id":70,"name":"Beinheben hängend","primaryMuscleGroup":"CORE","secondaryMuscleGroups":"","category":"EIGENGEWICHT","isCustom":false,"notes":"","isArchived":false},{"id":71,"name":"Beinheben liegend","primaryMuscleGroup":"CORE","secondaryMuscleGroups":"","category":"EIGENGEWICHT","isCustom":false,"notes":"","isArchived":false},{"id":72,"name":"Russian Twist","primaryMuscleGroup":"CORE","secondaryMuscleGroups":"","category":"EIGENGEWICHT","isCustom":false,"notes":"","isArchived":false},{"id":73,"name":"Ab-Roller","primaryMuscleGroup":"CORE","secondaryMuscleGroups":"","category":"EIGENGEWICHT","isCustom":false,"notes":"","isArchived":false},{"id":74,"name":"Seitliches Plank","primaryMuscleGroup":"CORE","secondaryMuscleGroups":"","category":"EIGENGEWICHT","isCustom":false,"notes":"","isArchived":false},{"id":75,"name":"Unterarmcurls","primaryMuscleGroup":"UNTERARME","secondaryMuscleGroups":"","category":"LANGHANTEL","isCustom":false,"notes":"","isArchived":false},{"id":76,"name":"Reverse Curls","primaryMuscleGroup":"UNTERARME","secondaryMuscleGroups":"BIZEPS","category":"LANGHANTEL","isCustom":false,"notes":"","isArchived":false},{"id":77,"name":"Wadenheben stehend (Maschine)","primaryMuscleGroup":"WADEN","secondaryMuscleGroups":"","category":"MASCHINE","isCustom":false,"notes":"","isArchived":false},{"id":78,"name":"Wadenheben sitzend","primaryMuscleGroup":"WADEN","secondaryMuscleGroups":"","category":"MASCHINE","isCustom":false,"notes":"","isArchived":false},{"id":79,"name":"Wadenheben an der Beinpresse","primaryMuscleGroup":"WADEN","secondaryMuscleGroups":"","category":"MASCHINE","isCustom":false,"notes":"","isArchived":false},{"id":80,"name":"Landmine Press (einarmig)","primaryMuscleGroup":"SCHULTERN","secondaryMuscleGroups":"TRIZEPS,BRUST","category":"LANGHANTEL","isCustom":true,"notes":"Griff „neutral“ – 45° Winkel; Ellbogen nah am Körper","isArchived":false},{"id":81,"name":"Kabel-Crunch kniend","primaryMuscleGroup":"CORE","secondaryMuscleGroups":"","category":"KABEL","isCustom":true,"notes":"","isArchived":false},{"id":82,"name":"Beinpresse 45° (Studio Süd)","primaryMuscleGroup":"BEINE","secondaryMuscleGroups":"GESAESS","category":"MASCHINE","isCustom":true,"notes":"Studio gewechselt","isArchived":true}],"workoutSessions":[{"id":1,"startTime":1788246000000,"endTime":1788249900000,"durationSeconds":3900,"name":"Push A (Kraft)","notes":"Gute Einheit – Schulter etwas steif.\nNächstes Mal länger aufwärmen.","planId":1,"metaPlanId":1,"isDeload":false},{"id":2,"startTime":1788418800000,"endTime":1788421800000,"durationSeconds":3000,"name":"Pull B","notes":"","planId":2,"metaPlanId":1,"isDeload":false},{"id":3,"startTime":1788591600000,"endTime":1788594300000,"durationSeconds":2700,"name":"Push A (Kraft)","notes":"","planId":1,"metaPlanId":1,"isDeload":false},{"id":4,"startTime":1788800400000,"endTime":1788802800000,"durationSeconds":2400,"name":"Beine & Rumpf","notes":"","planId":3,"metaPlanId":1,"isDeload":true},{"id":5,"startTime":1788937200000,"endTime":1788939000000,"durationSeconds":1800,"name":"Push A (Kraft)","notes":"","planId":1,"metaPlanId":null,"isDeload":null},{"id":6,"startTime":1789063200000,"endTime":1789066800000,"durationSeconds":3600,"name":"Freies Training","notes":"Sonderzeichen-Test: \"Anführungszeichen\", Backslash \\ und Tab\tEnde","planId":null,"metaPlanId":null,"isDeload":false},{"id":7,"startTime":1789126200000,"endTime":null,"durationSeconds":0,"name":"Laufendes Training","notes":"","planId":null,"metaPlanId":null,"isDeload":null}],"workoutSets":[{"id":1,"sessionId":1,"exerciseId":1,"setNumber":1,"reps":8,"weightKg":60.0,"setType":"WARMUP","completedAt":1788246120000,"rpe":null,"planTargetSnapshotId":1},{"id":2,"sessionId":1,"exerciseId":1,"setNumber":2,"reps":5,"weightKg":100.0,"setType":"NORMAL","completedAt":1788246240000,"rpe":7.0,"planTargetSnapshotId":1},{"id":3,"sessionId":1,"exerciseId":1,"setNumber":3,"reps":5,"weightKg":100.0,"setType":"NORMAL","completedAt":1788246360000,"rpe":7.5,"planTargetSnapshotId":1},{"id":4,"sessionId":1,"exerciseId":1,"setNumber":4,"reps":5,"weightKg":100.0,"setType":"NORMAL","completedAt":1788246480000,"rpe":8.0,"planTargetSnapshotId":1},{"id":5,"sessionId":1,"exerciseId":1,"setNumber":5,"reps":8,"weightKg":80.0,"setType":"DROP_SET","completedAt":1788246600000,"rpe":9.0,"planTargetSnapshotId":1},{"id":6,"sessionId":1,"exerciseId":3,"setNumber":1,"reps":12,"weightKg":60.0,"setType":"NORMAL","completedAt":1788246720000,"rpe":8.0,"planTargetSnapshotId":2},{"id":7,"sessionId":1,"exerciseId":3,"setNumber":2,"reps":12,"weightKg":60.0,"setType":"NORMAL","completedAt":1788246840000,"rpe":8.5,"planTargetSnapshotId":2},{"id":8,"sessionId":1,"exerciseId":3,"setNumber":3,"reps":12,"weightKg":60.0,"setType":"NORMAL","completedAt":1788246960000,"rpe":9.0,"planTargetSnapshotId":2},{"id":9,"sessionId":1,"exerciseId":80,"setNumber":1,"reps":13,"weightKg":20.0,"setType":"NORMAL","completedAt":1788247080000,"rpe":null,"planTargetSnapshotId":3},{"id":10,"sessionId":1,"exerciseId":47,"setNumber":1,"reps":12,"weightKg":10.0,"setType":"NORMAL","completedAt":1788247200000,"rpe":8.0,"planTargetSnapshotId":4},{"id":11,"sessionId":1,"exerciseId":80,"setNumber":2,"reps":12,"weightKg":20.0,"setType":"NORMAL","completedAt":1788247320000,"rpe":null,"planTargetSnapshotId":3},{"id":12,"sessionId":1,"exerciseId":47,"setNumber":2,"reps":12,"weightKg":10.0,"setType":"NORMAL","completedAt":1788247440000,"rpe":7.5,"planTargetSnapshotId":4},{"id":13,"sessionId":1,"exerciseId":80,"setNumber":3,"reps":12,"weightKg":20.0,"setType":"NORMAL","completedAt":1788247560000,"rpe":null,"planTargetSnapshotId":3},{"id":14,"sessionId":1,"exerciseId":47,"setNumber":3,"reps":12,"weightKg":10.0,"setType":"NORMAL","completedAt":1788247680000,"rpe":8.5,"planTargetSnapshotId":4},{"id":15,"sessionId":1,"exerciseId":61,"setNumber":1,"reps":12,"weightKg":20.0,"setType":"WARMUP","completedAt":1788247800000,"rpe":null,"planTargetSnapshotId":5},{"id":16,"sessionId":1,"exerciseId":61,"setNumber":2,"reps":10,"weightKg":35.0,"setType":"NORMAL","completedAt":1788247920000,"rpe":null,"planTargetSnapshotId":5},{"id":17,"sessionId":1,"exerciseId":61,"setNumber":3,"reps":10,"weightKg":35.0,"setType":"FAILURE","completedAt":1788248040000,"rpe":null,"planTargetSnapshotId":5},{"id":18,"sessionId":1,"exerciseId":61,"setNumber":4,"reps":15,"weightKg":25.0,"setType":"BACKOFF","completedAt":1788248160000,"rpe":null,"planTargetSnapshotId":5},{"id":19,"sessionId":2,"exerciseId":13,"setNumber":1,"reps":8,"weightKg":70.0,"setType":"NORMAL","completedAt":1788418920000,"rpe":null,"planTargetSnapshotId":6},{"id":20,"sessionId":2,"exerciseId":13,"setNumber":2,"reps":8,"weightKg":70.0,"setType":"NORMAL","completedAt":1788419040000,"rpe":null,"planTargetSnapshotId":6},{"id":21,"sessionId":2,"exerciseId":13,"setNumber":3,"reps":7,"weightKg":70.0,"setType":"NORMAL","completedAt":1788419160000,"rpe":null,"planTargetSnapshotId":6},{"id":22,"sessionId":2,"exerciseId":15,"setNumber":1,"reps":6,"weightKg":0.0,"setType":"NORMAL","completedAt":1788419280000,"rpe":null,"planTargetSnapshotId":7},{"id":23,"sessionId":2,"exerciseId":15,"setNumber":2,"reps":6,"weightKg":0.0,"setType":"NORMAL","completedAt":1788419400000,"rpe":null,"planTargetSnapshotId":7},{"id":24,"sessionId":2,"exerciseId":15,"setNumber":3,"reps":5,"weightKg":0.0,"setType":"NORMAL","completedAt":1788419520000,"rpe":null,"planTargetSnapshotId":7},{"id":25,"sessionId":2,"exerciseId":15,"setNumber":4,"reps":4,"weightKg":0.0,"setType":"FAILURE","completedAt":1788419640000,"rpe":null,"planTargetSnapshotId":7},{"id":26,"sessionId":2,"exerciseId":81,"setNumber":1,"reps":15,"weightKg":30.0,"setType":"NORMAL","completedAt":1788419760000,"rpe":null,"planTargetSnapshotId":8},{"id":27,"sessionId":2,"exerciseId":81,"setNumber":2,"reps":15,"weightKg":30.0,"setType":"NORMAL","completedAt":1788419880000,"rpe":null,"planTargetSnapshotId":8},{"id":28,"sessionId":2,"exerciseId":81,"setNumber":3,"reps":15,"weightKg":30.0,"setType":"NORMAL","completedAt":1788420000000,"rpe":null,"planTargetSnapshotId":8},{"id":29,"sessionId":3,"exerciseId":1,"setNumber":1,"reps":5,"weightKg":100.0,"setType":"NORMAL","completedAt":1788591720000,"rpe":7.5,"planTargetSnapshotId":9},{"id":30,"sessionId":3,"exerciseId":1,"setNumber":2,"reps":5,"weightKg":100.0,"setType":"NORMAL","completedAt":1788591840000,"rpe":8.0,"planTargetSnapshotId":9},{"id":31,"sessionId":3,"exerciseId":1,"setNumber":3,"reps":5,"weightKg":100.0,"setType":"NORMAL","completedAt":1788591960000,"rpe":8.5,"planTargetSnapshotId":9},{"id":32,"sessionId":3,"exerciseId":3,"setNumber":1,"reps":8,"weightKg":62.5,"setType":"NORMAL","completedAt":1788592080000,"rpe":null,"planTargetSnapshotId":10},{"id":33,"sessionId":3,"exerciseId":3,"setNumber":2,"reps":8,"weightKg":62.5,"setType":"NORMAL","completedAt":1788592200000,"rpe":null,"planTargetSnapshotId":10},{"id":34,"sessionId":3,"exerciseId":3,"setNumber":3,"reps":7,"weightKg":62.5,"setType":"NORMAL","completedAt":1788592320000,"rpe":null,"planTargetSnapshotId":10},{"id":35,"sessionId":3,"exerciseId":80,"setNumber":1,"reps":11,"weightKg":22.5,"setType":"NORMAL","completedAt":1788592440000,"rpe":null,"planTargetSnapshotId":11},{"id":36,"sessionId":4,"exerciseId":26,"setNumber":1,"reps":5,"weightKg":60.0,"setType":"WARMUP","completedAt":1788800520000,"rpe":null,"planTargetSnapshotId":14},{"id":37,"sessionId":4,"exerciseId":26,"setNumber":2,"reps":5,"weightKg":90.0,"setType":"NORMAL","completedAt":1788800640000,"rpe":null,"planTargetSnapshotId":14},{"id":38,"sessionId":4,"exerciseId":26,"setNumber":3,"reps":5,"weightKg":90.0,"setType":"NORMAL","completedAt":1788800760000,"rpe":null,"planTargetSnapshotId":14},{"id":39,"sessionId":4,"exerciseId":26,"setNumber":4,"reps":5,"weightKg":90.0,"setType":"NORMAL","completedAt":1788800880000,"rpe":null,"planTargetSnapshotId":14},{"id":40,"sessionId":5,"exerciseId":1,"setNumber":1,"reps":5,"weightKg":100.0,"setType":"NORMAL","completedAt":1788937320000,"rpe":8.0,"planTargetSnapshotId":15},{"id":41,"sessionId":5,"exerciseId":1,"setNumber":2,"reps":5,"weightKg":100.0,"setType":"NORMAL","completedAt":1788937440000,"rpe":8.0,"planTargetSnapshotId":15},{"id":42,"sessionId":5,"exerciseId":1,"setNumber":3,"reps":5,"weightKg":100.0,"setType":"NORMAL","completedAt":1788937560000,"rpe":8.5,"planTargetSnapshotId":15},{"id":43,"sessionId":6,"exerciseId":22,"setNumber":1,"reps":5,"weightKg":100.0,"setType":"WARMUP","completedAt":1789063320000,"rpe":null,"planTargetSnapshotId":null},{"id":44,"sessionId":6,"exerciseId":22,"setNumber":2,"reps":5,"weightKg":140.0,"setType":"NORMAL","completedAt":1789063440000,"rpe":8.0,"planTargetSnapshotId":null},{"id":45,"sessionId":6,"exerciseId":22,"setNumber":3,"reps":0,"weightKg":160.0,"setType":"NORMAL","completedAt":1789063560000,"rpe":null,"planTargetSnapshotId":null},{"id":46,"sessionId":6,"exerciseId":22,"setNumber":4,"reps":8,"weightKg":110.0,"setType":"BACKOFF","completedAt":1789063680000,"rpe":null,"planTargetSnapshotId":null},{"id":47,"sessionId":6,"exerciseId":82,"setNumber":1,"reps":12,"weightKg":180.0,"setType":"NORMAL","completedAt":1789063800000,"rpe":null,"planTargetSnapshotId":null},{"id":48,"sessionId":6,"exerciseId":82,"setNumber":2,"reps":10,"weightKg":200.0,"setType":"DROP_SET","completedAt":1789063920000,"rpe":null,"planTargetSnapshotId":null},{"id":49,"sessionId":7,"exerciseId":15,"setNumber":1,"reps":8,"weightKg":0.0,"setType":"NORMAL","completedAt":1789126320000,"rpe":null,"planTargetSnapshotId":null}],"trainingPlans":[{"id":1,"name":"Push A (Kraft)","createdAt":1788243000000},{"id":2,"name":"Pull B","createdAt":1788243300000},{"id":3,"name":"Beine & Rumpf","createdAt":1788243600000}],"planExercises":[{"id":1,"planId":1,"exerciseId":1,"orderIndex":0,"supersetGroupId":null,"targetSets":3,"targetReps":5,"targetWeightKg":100.0,"progression":{"scheme":"LINEAR","incrementValue":2.5,"incrementUnit":"METRIC","incrementKg":2.5,"minReps":null,"maxReps":null,"targetTotalReps":null,"targetRpe":null,"rpeTolerance":null,"stallThreshold":2,"backoffPercent":10.0,"ruleRevision":2,"successThreshold":2},"setTargets":[]},{"id":2,"planId":1,"exerciseId":3,"orderIndex":1,"supersetGroupId":null,"targetSets":3,"targetReps":8,"targetWeightKg":62.5,"progression":{"scheme":"DOUBLE","incrementValue":2.5,"incrementUnit":"METRIC","incrementKg":2.5,"minReps":8,"maxReps":12,"targetTotalReps":null,"targetRpe":null,"rpeTolerance":null,"stallThreshold":2,"backoffPercent":10.0,"ruleRevision":2,"successThreshold":1},"setTargets":[]},{"id":3,"planId":1,"exerciseId":80,"orderIndex":2,"supersetGroupId":1,"targetSets":3,"targetReps":11,"targetWeightKg":22.267961850050177,"progression":{"scheme":"TOTAL_REPS","incrementValue":5.0,"incrementUnit":"IMPERIAL","incrementKg":2.267961850050177,"minReps":null,"maxReps":null,"targetTotalReps":36,"targetRpe":null,"rpeTolerance":null,"stallThreshold":3,"backoffPercent":7.5,"ruleRevision":2,"successThreshold":1},"setTargets":[]},{"id":4,"planId":1,"exerciseId":47,"orderIndex":3,"supersetGroupId":1,"targetSets":3,"targetReps":12,"targetWeightKg":10.0,"progression":{"scheme":"RPE_RIR","incrementValue":1.0,"incrementUnit":"METRIC","incrementKg":1.0,"minReps":null,"maxReps":null,"targetTotalReps":null,"targetRpe":8.0,"rpeTolerance":0.5,"stallThreshold":2,"backoffPercent":10.0,"ruleRevision":2,"successThreshold":1},"setTargets":[]},{"id":5,"planId":1,"exerciseId":61,"orderIndex":4,"supersetGroupId":null,"targetSets":3,"targetReps":10,"targetWeightKg":35.0,"progression":{"scheme":"MANUAL","incrementValue":null,"incrementUnit":null,"incrementKg":null,"minReps":null,"maxReps":null,"targetTotalReps":null,"targetRpe":null,"rpeTolerance":null,"stallThreshold":2,"backoffPercent":10.0,"ruleRevision":1,"successThreshold":1},"setTargets":[{"kind":"WARMUP","reps":12,"weightKg":20.0},{"kind":"NORMAL","reps":10,"weightKg":35.0},{"kind":"NORMAL","reps":10,"weightKg":35.0},{"kind":"BACKOFF","reps":15,"weightKg":25.0}]},{"id":6,"planId":2,"exerciseId":13,"orderIndex":0,"supersetGroupId":null,"targetSets":3,"targetReps":8,"targetWeightKg":70.0,"progression":{"scheme":"LINEAR","incrementValue":2.5,"incrementUnit":"METRIC","incrementKg":2.5,"minReps":null,"maxReps":null,"targetTotalReps":null,"targetRpe":null,"rpeTolerance":null,"stallThreshold":2,"backoffPercent":10.0,"ruleRevision":1,"successThreshold":1},"setTargets":[]},{"id":7,"planId":2,"exerciseId":15,"orderIndex":1,"supersetGroupId":null,"targetSets":3,"targetReps":6,"targetWeightKg":0.0,"progression":{"scheme":"MANUAL","incrementValue":null,"incrementUnit":null,"incrementKg":null,"minReps":null,"maxReps":null,"targetTotalReps":null,"targetRpe":null,"rpeTolerance":null,"stallThreshold":2,"backoffPercent":10.0,"ruleRevision":1,"successThreshold":1},"setTargets":[]},{"id":8,"planId":2,"exerciseId":81,"orderIndex":2,"supersetGroupId":null,"targetSets":3,"targetReps":15,"targetWeightKg":30.0,"progression":{"scheme":"LINEAR","incrementValue":2.5,"incrementUnit":"METRIC","incrementKg":2.5,"minReps":null,"maxReps":null,"targetTotalReps":null,"targetRpe":null,"rpeTolerance":null,"stallThreshold":2,"backoffPercent":10.0,"ruleRevision":3,"successThreshold":1},"setTargets":[]},{"id":9,"planId":3,"exerciseId":26,"orderIndex":0,"supersetGroupId":null,"targetSets":3,"targetReps":5,"targetWeightKg":120.0,"progression":{"scheme":"LINEAR","incrementValue":5.0,"incrementUnit":"METRIC","incrementKg":5.0,"minReps":null,"maxReps":null,"targetTotalReps":null,"targetRpe":null,"rpeTolerance":null,"stallThreshold":2,"backoffPercent":10.0,"ruleRevision":2,"successThreshold":1},"setTargets":[]}],"personalRecords":[{"id":1,"exerciseId":1,"type":"MAX_WEIGHT","value":100.0,"achievedAt":1788937560000},{"id":2,"exerciseId":1,"type":"MAX_REPS","value":8.0,"achievedAt":1788246600000},{"id":3,"exerciseId":1,"type":"MAX_E1RM","value":116.66666666666667,"achievedAt":1788937560000},{"id":4,"exerciseId":3,"type":"MAX_WEIGHT","value":62.5,"achievedAt":1788592320000},{"id":5,"exerciseId":3,"type":"MAX_REPS","value":12.0,"achievedAt":1788246960000},{"id":6,"exerciseId":3,"type":"MAX_E1RM","value":84.0,"achievedAt":1788246960000},{"id":7,"exerciseId":80,"type":"MAX_WEIGHT","value":22.5,"achievedAt":1788592440000},{"id":8,"exerciseId":80,"type":"MAX_REPS","value":13.0,"achievedAt":1788247080000},{"id":9,"exerciseId":80,"type":"MAX_E1RM","value":30.75,"achievedAt":1788592440000},{"id":10,"exerciseId":47,"type":"MAX_WEIGHT","value":10.0,"achievedAt":1788247680000},{"id":11,"exerciseId":47,"type":"MAX_REPS","value":12.0,"achievedAt":1788247680000},{"id":12,"exerciseId":47,"type":"MAX_E1RM","value":14.0,"achievedAt":1788247680000},{"id":13,"exerciseId":61,"type":"MAX_WEIGHT","value":35.0,"achievedAt":1788248040000},{"id":14,"exerciseId":61,"type":"MAX_REPS","value":15.0,"achievedAt":1788248160000},{"id":15,"exerciseId":61,"type":"MAX_E1RM","value":46.666666666666664,"achievedAt":1788248040000},{"id":16,"exerciseId":1,"type":"MAX_VOLUME","value":2140.0,"achievedAt":1788246600000},{"id":17,"exerciseId":3,"type":"MAX_VOLUME","value":2160.0,"achievedAt":1788246960000},{"id":18,"exerciseId":47,"type":"MAX_VOLUME","value":360.0,"achievedAt":1788247680000},{"id":19,"exerciseId":61,"type":"MAX_VOLUME","value":1075.0,"achievedAt":1788248160000},{"id":20,"exerciseId":80,"type":"MAX_VOLUME","value":740.0,"achievedAt":1788247560000},{"id":21,"exerciseId":13,"type":"MAX_WEIGHT","value":70.0,"achievedAt":1788419160000},{"id":22,"exerciseId":13,"type":"MAX_REPS","value":8.0,"achievedAt":1788419040000},{"id":23,"exerciseId":13,"type":"MAX_E1RM","value":88.66666666666666,"achievedAt":1788419040000},{"id":24,"exerciseId":15,"type":"MAX_REPS","value":8.0,"achievedAt":1789126320000},{"id":25,"exerciseId":81,"type":"MAX_WEIGHT","value":30.0,"achievedAt":1788420000000},{"id":26,"exerciseId":81,"type":"MAX_REPS","value":15.0,"achievedAt":1788420000000},{"id":27,"exerciseId":81,"type":"MAX_E1RM","value":45.0,"achievedAt":1788420000000},{"id":28,"exerciseId":13,"type":"MAX_VOLUME","value":1610.0,"achievedAt":1788419160000},{"id":29,"exerciseId":81,"type":"MAX_VOLUME","value":1350.0,"achievedAt":1788420000000},{"id":30,"exerciseId":26,"type":"MAX_WEIGHT","value":90.0,"achievedAt":1788800880000},{"id":31,"exerciseId":26,"type":"MAX_REPS","value":5.0,"achievedAt":1788800880000},{"id":32,"exerciseId":26,"type":"MAX_E1RM","value":105.0,"achievedAt":1788800880000},{"id":33,"exerciseId":26,"type":"MAX_VOLUME","value":1350.0,"achievedAt":1788800880000},{"id":34,"exerciseId":22,"type":"MAX_WEIGHT","value":160.0,"achievedAt":1789063560000},{"id":35,"exerciseId":22,"type":"MAX_REPS","value":8.0,"achievedAt":1789063680000},{"id":36,"exerciseId":22,"type":"MAX_E1RM","value":163.33333333333334,"achievedAt":1789063440000},{"id":37,"exerciseId":82,"type":"MAX_WEIGHT","value":200.0,"achievedAt":1789063920000},{"id":38,"exerciseId":82,"type":"MAX_REPS","value":12.0,"achievedAt":1789063800000},{"id":39,"exerciseId":82,"type":"MAX_E1RM","value":266.66666666666663,"achievedAt":1789063920000},{"id":40,"exerciseId":22,"type":"MAX_VOLUME","value":1580.0,"achievedAt":1789063680000},{"id":41,"exerciseId":82,"type":"MAX_VOLUME","value":4160.0,"achievedAt":1789063920000}],"metaTrainingPlans":[{"id":1,"name":"Ganzkörper-Rotation","createdAt":1788243900000}],"metaPlanItems":[{"id":1,"metaPlanId":1,"trainingPlanId":1,"orderIndex":0},{"id":2,"metaPlanId":1,"trainingPlanId":2,"orderIndex":1},{"id":3,"metaPlanId":1,"trainingPlanId":3,"orderIndex":2}],"metaPlanSkips":[{"id":1,"metaPlanId":1,"trainingPlanId":3,"skippedAt":1788512400000}],"workoutPlanTargets":[{"id":1,"sessionId":1,"planId":1,"exerciseId":1,"orderIndex":0,"supersetGroupId":null,"target":{"sets":3,"reps":5,"weightKg":100.0},"progression":{"scheme":"LINEAR","incrementValue":2.5,"incrementUnit":"METRIC","incrementKg":2.5,"minReps":null,"maxReps":null,"targetTotalReps":null,"targetRpe":null,"rpeTolerance":null,"stallThreshold":2,"backoffPercent":10.0,"ruleRevision":2,"successThreshold":2},"setTargets":[]},{"id":2,"sessionId":1,"planId":1,"exerciseId":3,"orderIndex":1,"supersetGroupId":null,"target":{"sets":3,"reps":8,"weightKg":60.0},"progression":{"scheme":"DOUBLE","incrementValue":2.5,"incrementUnit":"METRIC","incrementKg":2.5,"minReps":8,"maxReps":12,"targetTotalReps":null,"targetRpe":null,"rpeTolerance":null,"stallThreshold":2,"backoffPercent":10.0,"ruleRevision":2,"successThreshold":1},"setTargets":[]},{"id":3,"sessionId":1,"planId":1,"exerciseId":80,"orderIndex":2,"supersetGroupId":1,"target":{"sets":3,"reps":10,"weightKg":20.0},"progression":{"scheme":"TOTAL_REPS","incrementValue":5.0,"incrementUnit":"IMPERIAL","incrementKg":2.267961850050177,"minReps":null,"maxReps":null,"targetTotalReps":36,"targetRpe":null,"rpeTolerance":null,"stallThreshold":3,"backoffPercent":7.5,"ruleRevision":2,"successThreshold":1},"setTargets":[]},{"id":4,"sessionId":1,"planId":1,"exerciseId":47,"orderIndex":3,"supersetGroupId":1,"target":{"sets":3,"reps":12,"weightKg":10.0},"progression":{"scheme":"RPE_RIR","incrementValue":1.0,"incrementUnit":"METRIC","incrementKg":1.0,"minReps":null,"maxReps":null,"targetTotalReps":null,"targetRpe":8.0,"rpeTolerance":0.5,"stallThreshold":2,"backoffPercent":10.0,"ruleRevision":2,"successThreshold":1},"setTargets":[]},{"id":5,"sessionId":1,"planId":1,"exerciseId":61,"orderIndex":4,"supersetGroupId":null,"target":{"sets":3,"reps":10,"weightKg":35.0},"progression":{"scheme":"MANUAL","incrementValue":null,"incrementUnit":null,"incrementKg":null,"minReps":null,"maxReps":null,"targetTotalReps":null,"targetRpe":null,"rpeTolerance":null,"stallThreshold":2,"backoffPercent":10.0,"ruleRevision":1,"successThreshold":1},"setTargets":[{"kind":"WARMUP","reps":12,"weightKg":20.0},{"kind":"NORMAL","reps":10,"weightKg":35.0},{"kind":"NORMAL","reps":10,"weightKg":35.0},{"kind":"BACKOFF","reps":15,"weightKg":25.0}]},{"id":6,"sessionId":2,"planId":2,"exerciseId":13,"orderIndex":0,"supersetGroupId":null,"target":{"sets":3,"reps":8,"weightKg":70.0},"progression":{"scheme":"LINEAR","incrementValue":2.5,"incrementUnit":"METRIC","incrementKg":2.5,"minReps":null,"maxReps":null,"targetTotalReps":null,"targetRpe":null,"rpeTolerance":null,"stallThreshold":2,"backoffPercent":10.0,"ruleRevision":1,"successThreshold":1},"setTargets":[]},{"id":7,"sessionId":2,"planId":2,"exerciseId":15,"orderIndex":1,"supersetGroupId":null,"target":{"sets":3,"reps":6,"weightKg":0.0},"progression":{"scheme":"MANUAL","incrementValue":null,"incrementUnit":null,"incrementKg":null,"minReps":null,"maxReps":null,"targetTotalReps":null,"targetRpe":null,"rpeTolerance":null,"stallThreshold":2,"backoffPercent":10.0,"ruleRevision":1,"successThreshold":1},"setTargets":[]},{"id":8,"sessionId":2,"planId":2,"exerciseId":81,"orderIndex":2,"supersetGroupId":null,"target":{"sets":3,"reps":15,"weightKg":30.0},"progression":{"scheme":"LINEAR","incrementValue":2.5,"incrementUnit":"METRIC","incrementKg":2.5,"minReps":null,"maxReps":null,"targetTotalReps":null,"targetRpe":null,"rpeTolerance":null,"stallThreshold":2,"backoffPercent":10.0,"ruleRevision":3,"successThreshold":1},"setTargets":[]},{"id":9,"sessionId":3,"planId":1,"exerciseId":1,"orderIndex":0,"supersetGroupId":null,"target":{"sets":3,"reps":5,"weightKg":100.0},"progression":{"scheme":"LINEAR","incrementValue":2.5,"incrementUnit":"METRIC","incrementKg":2.5,"minReps":null,"maxReps":null,"targetTotalReps":null,"targetRpe":null,"rpeTolerance":null,"stallThreshold":2,"backoffPercent":10.0,"ruleRevision":2,"successThreshold":2},"setTargets":[]},{"id":10,"sessionId":3,"planId":1,"exerciseId":3,"orderIndex":1,"supersetGroupId":null,"target":{"sets":3,"reps":8,"weightKg":62.5},"progression":{"scheme":"DOUBLE","incrementValue":2.5,"incrementUnit":"METRIC","incrementKg":2.5,"minReps":8,"maxReps":12,"targetTotalReps":null,"targetRpe":null,"rpeTolerance":null,"stallThreshold":2,"backoffPercent":10.0,"ruleRevision":2,"successThreshold":1},"setTargets":[]},{"id":11,"sessionId":3,"planId":1,"exerciseId":80,"orderIndex":2,"supersetGroupId":1,"target":{"sets":3,"reps":11,"weightKg":22.267961850050177},"progression":{"scheme":"TOTAL_REPS","incrementValue":5.0,"incrementUnit":"IMPERIAL","incrementKg":2.267961850050177,"minReps":null,"maxReps":null,"targetTotalReps":36,"targetRpe":null,"rpeTolerance":null,"stallThreshold":3,"backoffPercent":7.5,"ruleRevision":2,"successThreshold":1},"setTargets":[]},{"id":12,"sessionId":3,"planId":1,"exerciseId":47,"orderIndex":3,"supersetGroupId":1,"target":{"sets":3,"reps":12,"weightKg":10.0},"progression":{"scheme":"RPE_RIR","incrementValue":1.0,"incrementUnit":"METRIC","incrementKg":1.0,"minReps":null,"maxReps":null,"targetTotalReps":null,"targetRpe":8.0,"rpeTolerance":0.5,"stallThreshold":2,"backoffPercent":10.0,"ruleRevision":2,"successThreshold":1},"setTargets":[]},{"id":13,"sessionId":3,"planId":1,"exerciseId":61,"orderIndex":4,"supersetGroupId":null,"target":{"sets":3,"reps":10,"weightKg":35.0},"progression":{"scheme":"MANUAL","incrementValue":null,"incrementUnit":null,"incrementKg":null,"minReps":null,"maxReps":null,"targetTotalReps":null,"targetRpe":null,"rpeTolerance":null,"stallThreshold":2,"backoffPercent":10.0,"ruleRevision":1,"successThreshold":1},"setTargets":[{"kind":"WARMUP","reps":12,"weightKg":20.0},{"kind":"NORMAL","reps":10,"weightKg":35.0},{"kind":"NORMAL","reps":10,"weightKg":35.0},{"kind":"BACKOFF","reps":15,"weightKg":25.0}]},{"id":14,"sessionId":4,"planId":3,"exerciseId":26,"orderIndex":0,"supersetGroupId":null,"target":{"sets":3,"reps":5,"weightKg":120.0},"progression":{"scheme":"LINEAR","incrementValue":5.0,"incrementUnit":"METRIC","incrementKg":5.0,"minReps":null,"maxReps":null,"targetTotalReps":null,"targetRpe":null,"rpeTolerance":null,"stallThreshold":2,"backoffPercent":10.0,"ruleRevision":2,"successThreshold":1},"setTargets":[]},{"id":15,"sessionId":5,"planId":1,"exerciseId":1,"orderIndex":0,"supersetGroupId":null,"target":{"sets":3,"reps":5,"weightKg":100.0},"progression":{"scheme":"LINEAR","incrementValue":2.5,"incrementUnit":"METRIC","incrementKg":2.5,"minReps":null,"maxReps":null,"targetTotalReps":null,"targetRpe":null,"rpeTolerance":null,"stallThreshold":2,"backoffPercent":10.0,"ruleRevision":2,"successThreshold":2},"setTargets":[]},{"id":16,"sessionId":5,"planId":1,"exerciseId":3,"orderIndex":1,"supersetGroupId":null,"target":{"sets":3,"reps":8,"weightKg":62.5},"progression":{"scheme":"DOUBLE","incrementValue":2.5,"incrementUnit":"METRIC","incrementKg":2.5,"minReps":8,"maxReps":12,"targetTotalReps":null,"targetRpe":null,"rpeTolerance":null,"stallThreshold":2,"backoffPercent":10.0,"ruleRevision":2,"successThreshold":1},"setTargets":[]},{"id":17,"sessionId":5,"planId":1,"exerciseId":80,"orderIndex":2,"supersetGroupId":1,"target":{"sets":3,"reps":11,"weightKg":22.267961850050177},"progression":{"scheme":"TOTAL_REPS","incrementValue":5.0,"incrementUnit":"IMPERIAL","incrementKg":2.267961850050177,"minReps":null,"maxReps":null,"targetTotalReps":36,"targetRpe":null,"rpeTolerance":null,"stallThreshold":3,"backoffPercent":7.5,"ruleRevision":2,"successThreshold":1},"setTargets":[]},{"id":18,"sessionId":5,"planId":1,"exerciseId":47,"orderIndex":3,"supersetGroupId":1,"target":{"sets":3,"reps":12,"weightKg":10.0},"progression":{"scheme":"RPE_RIR","incrementValue":1.0,"incrementUnit":"METRIC","incrementKg":1.0,"minReps":null,"maxReps":null,"targetTotalReps":null,"targetRpe":8.0,"rpeTolerance":0.5,"stallThreshold":2,"backoffPercent":10.0,"ruleRevision":2,"successThreshold":1},"setTargets":[]},{"id":19,"sessionId":5,"planId":1,"exerciseId":61,"orderIndex":4,"supersetGroupId":null,"target":{"sets":3,"reps":10,"weightKg":35.0},"progression":{"scheme":"MANUAL","incrementValue":null,"incrementUnit":null,"incrementKg":null,"minReps":null,"maxReps":null,"targetTotalReps":null,"targetRpe":null,"rpeTolerance":null,"stallThreshold":2,"backoffPercent":10.0,"ruleRevision":1,"successThreshold":1},"setTargets":[{"kind":"WARMUP","reps":12,"weightKg":20.0},{"kind":"NORMAL","reps":10,"weightKg":35.0},{"kind":"NORMAL","reps":10,"weightKg":35.0},{"kind":"BACKOFF","reps":15,"weightKg":25.0}]}],"progressionSuggestions":[{"id":1,"sourceSessionId":1,"sourceTargetSnapshotId":1,"planId":1,"exerciseId":1,"orderIndex":0,"supersetGroupId":null,"sourceTarget":{"sets":3,"reps":5,"weightKg":100.0},"sourceProgression":{"scheme":"LINEAR","incrementValue":2.5,"incrementUnit":"METRIC","incrementKg":2.5,"minReps":null,"maxReps":null,"targetTotalReps":null,"targetRpe":null,"rpeTolerance":null,"stallThreshold":2,"backoffPercent":10.0,"ruleRevision":2,"successThreshold":2},"outcomeType":"KEEP_TARGET","reasonCode":"SUCCESS_CONFIRMATION_REQUIRED","reasonArguments":{"actualReps":5.0,"actualWeightKg":100.0,"countedWorkSets":3.0,"failureMarkedSets":0.0,"ignoredBackoffSets":0.0,"ignoredDropSets":1.0,"ignoredExtraSets":0.0,"ignoredWarmupSets":1.0,"maxWeightKg":100.0,"minWeightKg":100.0,"requiredSuccesses":2.0,"successfulSessions":1.0,"targetReps":5.0},"countedSetIds":[2,3,4],"streakEffect":"RESET","suggestedTarget":null,"status":"INFORMATIONAL","wasEdited":false,"finalTarget":null,"createdAtEpochMillis":1788249900000,"decidedAtEpochMillis":null},{"id":2,"sourceSessionId":1,"sourceTargetSnapshotId":2,"planId":1,"exerciseId":3,"orderIndex":1,"supersetGroupId":null,"sourceTarget":{"sets":3,"reps":8,"weightKg":60.0},"sourceProgression":{"scheme":"DOUBLE","incrementValue":2.5,"incrementUnit":"METRIC","incrementKg":2.5,"minReps":8,"maxReps":12,"targetTotalReps":null,"targetRpe":null,"rpeTolerance":null,"stallThreshold":2,"backoffPercent":10.0,"ruleRevision":2,"successThreshold":1},"outcomeType":"PROPOSE_CHANGE","reasonCode":"LOAD_ADVANCED","reasonArguments":{"actualReps":12.0,"actualWeightKg":60.0,"countedWorkSets":3.0,"failureMarkedSets":0.0,"ignoredBackoffSets":0.0,"ignoredDropSets":0.0,"ignoredExtraSets":0.0,"ignoredWarmupSets":0.0,"maxWeightKg":60.0,"minWeightKg":60.0,"stepOriginalValue":2.5,"targetReps":8.0},"countedSetIds":[6,7,8],"streakEffect":"RESET","suggestedTarget":{"sets":3,"reps":8,"weightKg":62.5},"status":"ACCEPTED","wasEdited":false,"finalTarget":{"sets":3,"reps":8,"weightKg":62.5},"createdAtEpochMillis":1788249900000,"decidedAtEpochMillis":1788285600000},{"id":3,"sourceSessionId":1,"sourceTargetSnapshotId":3,"planId":1,"exerciseId":80,"orderIndex":2,"supersetGroupId":1,"sourceTarget":{"sets":3,"reps":10,"weightKg":20.0},"sourceProgression":{"scheme":"TOTAL_REPS","incrementValue":5.0,"incrementUnit":"IMPERIAL","incrementKg":2.267961850050177,"minReps":null,"maxReps":null,"targetTotalReps":36,"targetRpe":null,"rpeTolerance":null,"stallThreshold":3,"backoffPercent":7.5,"ruleRevision":2,"successThreshold":1},"outcomeType":"PROPOSE_CHANGE","reasonCode":"TOTAL_REPS_COMPLETED","reasonArguments":{"achievedTotalReps":37.0,"actualWeightKg":20.0,"countedWorkSets":3.0,"failureMarkedSets":0.0,"ignoredBackoffSets":0.0,"ignoredDropSets":0.0,"ignoredExtraSets":0.0,"ignoredWarmupSets":0.0,"maxWeightKg":20.0,"minWeightKg":20.0,"stepOriginalValue":5.0,"targetTotalReps":36.0},"countedSetIds":[9,11,13],"streakEffect":"RESET","suggestedTarget":{"sets":3,"reps":10,"weightKg":22.267961850050177},"status":"ACCEPTED","wasEdited":true,"finalTarget":{"sets":3,"reps":11,"weightKg":22.267961850050177},"createdAtEpochMillis":1788249900000,"decidedAtEpochMillis":1788285600000},{"id":4,"sourceSessionId":1,"sourceTargetSnapshotId":4,"planId":1,"exerciseId":47,"orderIndex":3,"supersetGroupId":1,"sourceTarget":{"sets":3,"reps":12,"weightKg":10.0},"sourceProgression":{"scheme":"RPE_RIR","incrementValue":1.0,"incrementUnit":"METRIC","incrementKg":1.0,"minReps":null,"maxReps":null,"targetTotalReps":null,"targetRpe":8.0,"rpeTolerance":0.5,"stallThreshold":2,"backoffPercent":10.0,"ruleRevision":2,"successThreshold":1},"outcomeType":"PROPOSE_CHANGE","reasonCode":"RPE_WITHIN_TARGET","reasonArguments":{"actualWeightKg":10.0,"countedWorkSets":3.0,"failureMarkedSets":0.0,"highestRpe":8.5,"ignoredBackoffSets":0.0,"ignoredDropSets":0.0,"ignoredExtraSets":0.0,"ignoredWarmupSets":0.0,"maxWeightKg":10.0,"minWeightKg":10.0,"stepOriginalValue":1.0,"targetRpe":8.0,"tolerance":0.5},"countedSetIds":[10,12,14],"streakEffect":"RESET","suggestedTarget":{"sets":3,"reps":12,"weightKg":11.0},"status":"REJECTED","wasEdited":false,"finalTarget":null,"createdAtEpochMillis":1788249900000,"decidedAtEpochMillis":1788285600000},{"id":5,"sourceSessionId":2,"sourceTargetSnapshotId":6,"planId":2,"exerciseId":13,"orderIndex":0,"supersetGroupId":null,"sourceTarget":{"sets":3,"reps":8,"weightKg":70.0},"sourceProgression":{"scheme":"LINEAR","incrementValue":2.5,"incrementUnit":"METRIC","incrementKg":2.5,"minReps":null,"maxReps":null,"targetTotalReps":null,"targetRpe":null,"rpeTolerance":null,"stallThreshold":2,"backoffPercent":10.0,"ruleRevision":1,"successThreshold":1},"outcomeType":"KEEP_TARGET","reasonCode":"REPEAT_TARGET","reasonArguments":{"actualReps":7.0,"actualWeightKg":70.0,"targetReps":8.0},"countedSetIds":[19,20,21],"streakEffect":"INCREMENT","suggestedTarget":null,"status":"INFORMATIONAL","wasEdited":false,"finalTarget":null,"createdAtEpochMillis":1788421800000,"decidedAtEpochMillis":null},{"id":6,"sourceSessionId":2,"sourceTargetSnapshotId":8,"planId":2,"exerciseId":81,"orderIndex":2,"supersetGroupId":null,"sourceTarget":{"sets":3,"reps":15,"weightKg":30.0},"sourceProgression":{"scheme":"LINEAR","incrementValue":2.5,"incrementUnit":"METRIC","incrementKg":2.5,"minReps":null,"maxReps":null,"targetTotalReps":null,"targetRpe":null,"rpeTolerance":null,"stallThreshold":2,"backoffPercent":10.0,"ruleRevision":3,"successThreshold":1},"outcomeType":"INSUFFICIENT_DATA","reasonCode":"RULE_REVISION_UNSUPPORTED","reasonArguments":{},"countedSetIds":[26,27,28],"streakEffect":"IGNORE","suggestedTarget":null,"status":"INFORMATIONAL","wasEdited":false,"finalTarget":null,"createdAtEpochMillis":1788421800000,"decidedAtEpochMillis":null},{"id":7,"sourceSessionId":3,"sourceTargetSnapshotId":9,"planId":1,"exerciseId":1,"orderIndex":0,"supersetGroupId":null,"sourceTarget":{"sets":3,"reps":5,"weightKg":100.0},"sourceProgression":{"scheme":"LINEAR","incrementValue":2.5,"incrementUnit":"METRIC","incrementKg":2.5,"minReps":null,"maxReps":null,"targetTotalReps":null,"targetRpe":null,"rpeTolerance":null,"stallThreshold":2,"backoffPercent":10.0,"ruleRevision":2,"successThreshold":2},"outcomeType":"PROPOSE_CHANGE","reasonCode":"LOAD_ADVANCED","reasonArguments":{"actualReps":5.0,"actualWeightKg":100.0,"countedWorkSets":3.0,"failureMarkedSets":0.0,"ignoredBackoffSets":0.0,"ignoredDropSets":0.0,"ignoredExtraSets":0.0,"ignoredWarmupSets":0.0,"maxWeightKg":100.0,"minWeightKg":100.0,"requiredSuccesses":2.0,"stepOriginalValue":2.5,"successfulSessions":2.0,"targetReps":5.0},"countedSetIds":[29,30,31],"streakEffect":"RESET","suggestedTarget":{"sets":3,"reps":5,"weightKg":102.5},"status":"STALE","wasEdited":false,"finalTarget":null,"createdAtEpochMillis":1788594300000,"decidedAtEpochMillis":1788634800000},{"id":8,"sourceSessionId":3,"sourceTargetSnapshotId":10,"planId":1,"exerciseId":3,"orderIndex":1,"supersetGroupId":null,"sourceTarget":{"sets":3,"reps":8,"weightKg":62.5},"sourceProgression":{"scheme":"DOUBLE","incrementValue":2.5,"incrementUnit":"METRIC","incrementKg":2.5,"minReps":8,"maxReps":12,"targetTotalReps":null,"targetRpe":null,"rpeTolerance":null,"stallThreshold":2,"backoffPercent":10.0,"ruleRevision":2,"successThreshold":1},"outcomeType":"KEEP_TARGET","reasonCode":"REPEAT_TARGET","reasonArguments":{"actualReps":7.0,"actualWeightKg":62.5,"countedWorkSets":3.0,"failedSessions":1.0,"failureMarkedSets":0.0,"failureThreshold":2.0,"ignoredBackoffSets":0.0,"ignoredDropSets":0.0,"ignoredExtraSets":0.0,"ignoredWarmupSets":0.0,"maxWeightKg":62.5,"minWeightKg":62.5,"targetReps":8.0},"countedSetIds":[32,33,34],"streakEffect":"INCREMENT","suggestedTarget":null,"status":"INFORMATIONAL","wasEdited":false,"finalTarget":null,"createdAtEpochMillis":1788594300000,"decidedAtEpochMillis":null},{"id":9,"sourceSessionId":3,"sourceTargetSnapshotId":11,"planId":1,"exerciseId":80,"orderIndex":2,"supersetGroupId":1,"sourceTarget":{"sets":3,"reps":11,"weightKg":22.267961850050177},"sourceProgression":{"scheme":"TOTAL_REPS","incrementValue":5.0,"incrementUnit":"IMPERIAL","incrementKg":2.267961850050177,"minReps":null,"maxReps":null,"targetTotalReps":36,"targetRpe":null,"rpeTolerance":null,"stallThreshold":3,"backoffPercent":7.5,"ruleRevision":2,"successThreshold":1},"outcomeType":"INSUFFICIENT_DATA","reasonCode":"TOO_FEW_WORK_SETS","reasonArguments":{"actualWorkSets":1.0,"countedWorkSets":1.0,"failureMarkedSets":0.0,"ignoredBackoffSets":0.0,"ignoredDropSets":0.0,"ignoredExtraSets":0.0,"ignoredWarmupSets":0.0,"targetSets":3.0},"countedSetIds":[35],"streakEffect":"IGNORE","suggestedTarget":null,"status":"INFORMATIONAL","wasEdited":false,"finalTarget":null,"createdAtEpochMillis":1788594300000,"decidedAtEpochMillis":null},{"id":10,"sourceSessionId":3,"sourceTargetSnapshotId":12,"planId":1,"exerciseId":47,"orderIndex":3,"supersetGroupId":1,"sourceTarget":{"sets":3,"reps":12,"weightKg":10.0},"sourceProgression":{"scheme":"RPE_RIR","incrementValue":1.0,"incrementUnit":"METRIC","incrementKg":1.0,"minReps":null,"maxReps":null,"targetTotalReps":null,"targetRpe":8.0,"rpeTolerance":0.5,"stallThreshold":2,"backoffPercent":10.0,"ruleRevision":2,"successThreshold":1},"outcomeType":"INSUFFICIENT_DATA","reasonCode":"TOO_FEW_WORK_SETS","reasonArguments":{"actualWorkSets":0.0,"countedWorkSets":0.0,"failureMarkedSets":0.0,"ignoredBackoffSets":0.0,"ignoredDropSets":0.0,"ignoredExtraSets":0.0,"ignoredWarmupSets":0.0,"targetSets":3.0},"countedSetIds":[],"streakEffect":"IGNORE","suggestedTarget":null,"status":"INFORMATIONAL","wasEdited":false,"finalTarget":null,"createdAtEpochMillis":1788594300000,"decidedAtEpochMillis":null},{"id":11,"sourceSessionId":5,"sourceTargetSnapshotId":15,"planId":1,"exerciseId":1,"orderIndex":0,"supersetGroupId":null,"sourceTarget":{"sets":3,"reps":5,"weightKg":100.0},"sourceProgression":{"scheme":"LINEAR","incrementValue":2.5,"incrementUnit":"METRIC","incrementKg":2.5,"minReps":null,"maxReps":null,"targetTotalReps":null,"targetRpe":null,"rpeTolerance":null,"stallThreshold":2,"backoffPercent":10.0,"ruleRevision":2,"successThreshold":2},"outcomeType":"PROPOSE_CHANGE","reasonCode":"LOAD_ADVANCED","reasonArguments":{"actualReps":5.0,"actualWeightKg":100.0,"countedWorkSets":3.0,"failureMarkedSets":0.0,"ignoredBackoffSets":0.0,"ignoredDropSets":0.0,"ignoredExtraSets":0.0,"ignoredWarmupSets":0.0,"maxWeightKg":100.0,"minWeightKg":100.0,"requiredSuccesses":2.0,"stepOriginalValue":2.5,"successfulSessions":2.0,"targetReps":5.0},"countedSetIds":[40,41,42],"streakEffect":"RESET","suggestedTarget":{"sets":3,"reps":5,"weightKg":102.5},"status":"PENDING","wasEdited":false,"finalTarget":null,"createdAtEpochMillis":1788939000000,"decidedAtEpochMillis":null},{"id":12,"sourceSessionId":5,"sourceTargetSnapshotId":16,"planId":1,"exerciseId":3,"orderIndex":1,"supersetGroupId":null,"sourceTarget":{"sets":3,"reps":8,"weightKg":62.5},"sourceProgression":{"scheme":"DOUBLE","incrementValue":2.5,"incrementUnit":"METRIC","incrementKg":2.5,"minReps":8,"maxReps":12,"targetTotalReps":null,"targetRpe":null,"rpeTolerance":null,"stallThreshold":2,"backoffPercent":10.0,"ruleRevision":2,"successThreshold":1},"outcomeType":"INSUFFICIENT_DATA","reasonCode":"TOO_FEW_WORK_SETS","reasonArguments":{"actualWorkSets":0.0,"countedWorkSets":0.0,"failureMarkedSets":0.0,"ignoredBackoffSets":0.0,"ignoredDropSets":0.0,"ignoredExtraSets":0.0,"ignoredWarmupSets":0.0,"targetSets":3.0},"countedSetIds":[],"streakEffect":"IGNORE","suggestedTarget":null,"status":"INFORMATIONAL","wasEdited":false,"finalTarget":null,"createdAtEpochMillis":1788939000000,"decidedAtEpochMillis":null},{"id":13,"sourceSessionId":5,"sourceTargetSnapshotId":17,"planId":1,"exerciseId":80,"orderIndex":2,"supersetGroupId":1,"sourceTarget":{"sets":3,"reps":11,"weightKg":22.267961850050177},"sourceProgression":{"scheme":"TOTAL_REPS","incrementValue":5.0,"incrementUnit":"IMPERIAL","incrementKg":2.267961850050177,"minReps":null,"maxReps":null,"targetTotalReps":36,"targetRpe":null,"rpeTolerance":null,"stallThreshold":3,"backoffPercent":7.5,"ruleRevision":2,"successThreshold":1},"outcomeType":"INSUFFICIENT_DATA","reasonCode":"TOO_FEW_WORK_SETS","reasonArguments":{"actualWorkSets":0.0,"countedWorkSets":0.0,"failureMarkedSets":0.0,"ignoredBackoffSets":0.0,"ignoredDropSets":0.0,"ignoredExtraSets":0.0,"ignoredWarmupSets":0.0,"targetSets":3.0},"countedSetIds":[],"streakEffect":"IGNORE","suggestedTarget":null,"status":"INFORMATIONAL","wasEdited":false,"finalTarget":null,"createdAtEpochMillis":1788939000000,"decidedAtEpochMillis":null},{"id":14,"sourceSessionId":5,"sourceTargetSnapshotId":18,"planId":1,"exerciseId":47,"orderIndex":3,"supersetGroupId":1,"sourceTarget":{"sets":3,"reps":12,"weightKg":10.0},"sourceProgression":{"scheme":"RPE_RIR","incrementValue":1.0,"incrementUnit":"METRIC","incrementKg":1.0,"minReps":null,"maxReps":null,"targetTotalReps":null,"targetRpe":8.0,"rpeTolerance":0.5,"stallThreshold":2,"backoffPercent":10.0,"ruleRevision":2,"successThreshold":1},"outcomeType":"INSUFFICIENT_DATA","reasonCode":"TOO_FEW_WORK_SETS","reasonArguments":{"actualWorkSets":0.0,"countedWorkSets":0.0,"failureMarkedSets":0.0,"ignoredBackoffSets":0.0,"ignoredDropSets":0.0,"ignoredExtraSets":0.0,"ignoredWarmupSets":0.0,"targetSets":3.0},"countedSetIds":[],"streakEffect":"IGNORE","suggestedTarget":null,"status":"INFORMATIONAL","wasEdited":false,"finalTarget":null,"createdAtEpochMillis":1788939000000,"decidedAtEpochMillis":null}],"readinessData":{"formatVersion":1,"schemaVersion":1,"checkIns":[{"localDate":"2026-09-01","sleepQuality":4,"energy":3,"stress":2,"muscleSoreness":{"BRUST":2,"SCHULTERN":3},"recordedAtEpochMillis":1788244200000,"updatedAtEpochMillis":null},{"localDate":"2026-09-03","sleepQuality":2,"energy":null,"stress":null,"muscleSoreness":{},"recordedAtEpochMillis":1788417900000,"updatedAtEpochMillis":null},{"localDate":"2026-09-05","sleepQuality":null,"energy":4,"stress":3,"muscleSoreness":{"BEINE":4,"GESAESS":2},"recordedAtEpochMillis":1788589800000,"updatedAtEpochMillis":1788590400000}],"setIntentions":[{"setId":17,"intention":"PLANNED_FAILURE","recordedAtEpochMillis":1788248040000,"note":""},{"setId":18,"intention":"UNEXPECTED_TARGET_MISS","recordedAtEpochMillis":1788248160000,"note":"Wollte 15, Griff rutschte"},{"setId":21,"intention":"UNEXPECTED_TARGET_MISS","recordedAtEpochMillis":1788419160000,"note":""},{"setId":25,"intention":"PLANNED_FAILURE","recordedAtEpochMillis":1788419640000,"note":""},{"setId":45,"intention":"UNEXPECTED_TARGET_MISS","recordedAtEpochMillis":1789063560000,"note":""}]}}"""
}
