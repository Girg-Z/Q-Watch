# ORBIT Watch Face — Design Spec

_2026-06-06_

## Overview

Replace the current placeholder `watchface.xml` with the ORBIT design from the Defqon.1 Watch Faces design file. ORBIT is a dark AMOLED watch face with a stage-colored progress arc hugging the bezel, the performing artist's name as the dominant focal element, and secondary clock/countdown/next-artist information.

---

## 1. Data model

### `StageState` — new fields

```kotlin
val setProgressPercent: Int = 0      // 0–100; computed from now vs. set start/end
val minsToSetEnd: Int = 0            // max(0, ceil((end - now) in minutes))
val nextArtistName: String? = null   // artist in the next timetable slot on this stage
```

These are computed by `LocationForegroundService` (which already holds the parsed timetable in memory) and written to DataStore alongside the existing fields.

### Complication slots — 3 total

All served by `MainComplicationService`, routing on `request.complicationInstanceId` mapped to slot IDs defined in `watchface.xml`.

| slotId | Type | TEXT | TITLE | VALUE |
|--------|------|------|-------|-------|
| 0 | `SHORT_TEXT` | `stageId` | — | — |
| 1 | `RANGED_VALUE` | `artistName` | `minsToSetEnd.toString()` | `setProgressPercent.toFloat()` |
| 2 | `SHORT_TEXT` | `nextArtistName ?: ""` | — | — |

**Error state encoding (slot 0 TEXT):**
- `"gps_error"` — GPS permission denied or no fix
- `"between"` — no stage polygon matched, or festival not active

When slot 0 carries an error string, slots 1 and 2 emit empty/zero data.

---

## 2. WFF layout — active mode

Canvas: 450×450, always `#000000` background.

### Elements (top-to-bottom)

| Element | Type | Content | Position | Style |
|---------|------|---------|----------|-------|
| Background | `PartDraw > Rectangle` | fill `#000000` | 0,0 450×450 | — |
| Arc track | `PartDraw > Arc` | full 360° ring | center 225,225 r=207 stroke=13 | per-stage color at 16% alpha |
| Arc fill | `PartDraw > Arc` | sweep = `VALUE × 3.6°` from −90° | same | per-stage color, full alpha |
| Kicker | `PartText` | `NOW · [STAGE_NAME]` | x=0 y=148 w=450 h=20 | SYNC_TO_DEVICE 12px #6f6f6f center |
| Artist | `PartText` | `[COMPLICATION_1.TEXT]` | x=0 y=172 w=450 h=68 | SYNC_TO_DEVICE BOLD 52px per-stage color center |
| Clock | `DigitalClock` | `HH:mm` 24h | x=0 y=252 w=450 h=48 | SYNC_TO_DEVICE 36px #dcdcdc center |
| Countdown | `PartText` | `[COMPLICATION_1.TITLE]M LEFT` | x=0 y=303 w=450 h=20 | SYNC_TO_DEVICE 12px #6f6f6f center |
| Next | `PartText` | `NEXT · [COMPLICATION_2.TEXT]` | x=0 y=325 w=450 h=20 | SYNC_TO_DEVICE 12px #6f6f6f center |

**Per-stage color mapping** (slot 0 → arc color, artist color, kicker accent):

| stageId | Stage color |
|---------|-------------|
| `red` | `#FF0000` |
| `blue` | `#0BDBEF` |
| `black` | `#9A9A9A` |
| `indigo` | `#3842DA` |
| `brown` | `#936037` |
| `magenta` | `#FF008B` |
| `uv` | `#D492FF` |
| `green` | `#00FF00` |
| `yellow` | `#F1E300` |
| `gold` | `#BB9551` |
| `orange_light_district` | `#FF6500` |
| `purple` | `#A100FF` |
| `silver` | `#DADADA` |
| `pink` | `#EF81A0` |

**Stage name display strings** (kicker `NOW · X`):

Each `<Compare>` branch for a stageId hardcodes the human-readable stage name (e.g. `"RED"`, `"BLUE"`, `"U.V."`, `"ORANGE LIGHT DISTRICT"`).

### Error states (slot 0 = `"gps_error"` or `"between"`)

- Background: `#000000`
- Arc: not rendered
- Center: `"GPS UNAVAILABLE"` (gps_error) or nothing (between) in `#888888` 30px
- Clock: rendered as normal

---

## 3. WFF layout — ambient mode

| Element | Change from active |
|---------|--------------------|
| Arc fill | 3px stroke, per-stage color at 30% alpha |
| Arc track | hidden (alpha=0) |
| Kicker | alpha=0 |
| Artist | alpha=0 |
| Clock | switches to large 80px THIN centered at y=175 |
| Countdown | alpha=0 |
| Next | alpha=0 |

---

## 4. Complication services

Because slot 0 and slot 2 both use `SHORT_TEXT`, a single service cannot distinguish between them by complication type alone — Android routes by `complicationInstanceId` which isn't predictable at install time. The clean solution is **three separate service classes**, each registered in `AndroidManifest.xml` and pointed to by its slot's `DefaultProviderPolicy`:

- `StageComplicationService` → slot 0 (`SHORT_TEXT`, stageId)
- `NowPlayingComplicationService` → slot 1 (`RANGED_VALUE`, artist + minsLeft + progress)
- `NextArtistComplicationService` → slot 2 (`SHORT_TEXT`, nextArtistName)

All three read from the same `StageState` DataStore via a shared `readStageState()` helper. Each service is a thin wrapper:

```kotlin
// StageComplicationService
override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData {
    val state = applicationContext.readStageStateFlow().first()
    val text = when {
        !state.isFestivalActive || state.stageId == null -> "between"
        !state.isGpsAvailable -> "gps_error"
        else -> state.stageId
    }
    return makeShortText(text)
}

// NowPlayingComplicationService
override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData {
    val state = applicationContext.readStageStateFlow().first()
    return makeRangedValue(
        text  = state.artistName ?: "",
        title = state.minsToSetEnd.toString(),
        value = state.setProgressPercent.toFloat()
    )
}

// NextArtistComplicationService
override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData {
    val state = applicationContext.readStageStateFlow().first()
    return makeShortText(state.nextArtistName ?: "")
}
```

`requestUpdateAll()` calls in `LocationForegroundService` trigger all three services.

---

## 5. `LocationForegroundService` changes

After resolving stage + artist, compute and write the new fields:

```kotlin
val nowMin = now.hour * 60 + now.minute
val currentEvent = /* already resolved for artistName */
val progress = if (currentEvent != null)
    ((nowMin - currentEvent.startMin) * 100 / (currentEvent.endMin - currentEvent.startMin)).coerceIn(0, 100)
else 0
val minsLeft = if (currentEvent != null)
    maxOf(0, currentEvent.endMin - nowMin)
else 0
val nextArtist = timetable.nextEventAfter(stageName, currentEvent)?.name
```

---

## 6. `MainActivity` debug buttons

Each stage button writes the three new fields with fixed dummy values:

```kotlin
setProgressPercent = 60
minsToSetEnd = 37
nextArtistName = "Headhunterz"
```

The `between` and `gps_error` buttons write `setProgressPercent=0`, `minsToSetEnd=0`, `nextArtistName=null`.

---

## 7. Files changed

| File | Change |
|------|--------|
| `watchface/src/main/res/raw/watchface.xml` | Full rewrite — ORBIT design |
| `wear/.../data/StageState.kt` | Add 3 new fields |
| `wear/.../data/StageDataStore.kt` | Persist/read new fields |
| `wear/.../complication/MainComplicationService.kt` | Becomes `StageComplicationService`; add `NowPlayingComplicationService` + `NextArtistComplicationService` |
| `wear/.../service/LocationForegroundService.kt` | Compute and write new fields |
| `wear/.../presentation/MainActivity.kt` | Update debug buttons |

---

## 8. Out of scope

- Silver/Pink stages (not in the current complication service) — add them to the color map but they follow the same pattern as existing stages
- Font family: WFF uses `SYNC_TO_DEVICE` (system font); the Saira Condensed font from the design prototype is not embeddable in WFF
- Tap interactions on the watch face
