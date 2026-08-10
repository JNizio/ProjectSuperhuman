from pathlib import Path

p = Path('nextgen/androidApp/src/main/java/com/projectsuperhuman/next/NativeClinicalParity.kt')
s = p.read_text()

s = s.replace(
    'private data class OcrLine(val text: String, val block: Int, val top: Int, val left: Int)',
    'private data class OcrLine(val text: String, val block: Int, val top: Int, val left: Int, val bottom: Int = top, val right: Int = left)'
)

old = '''private fun extractOcrLines(ocr: MlText): List<OcrLine> {
    val all = mutableListOf<OcrLine>()
    ocr.textBlocks.forEachIndexed { blockIndex, block ->
        block.lines.forEach { line ->
            val box = line.boundingBox
            val text = line.text.replace('–', '-').replace('—', '-').trim()
            if (text.length > 1 && !clinicalBoilerplate(text)) {
                all += OcrLine(text, blockIndex, box?.top ?: Int.MAX_VALUE / 4, box?.left ?: 0)
            }
        }
    }
    return all.sortedWith(compareBy<OcrLine> { it.top }.thenBy { it.left })
}'''
new = '''private fun extractOcrLines(ocr: MlText): List<OcrLine> {
    val all = mutableListOf<OcrLine>()
    ocr.textBlocks.forEachIndexed { blockIndex, block ->
        block.lines.forEach { line ->
            val box = line.boundingBox
            val text = line.text.replace('–', '-').replace('—', '-').trim()
            if (text.length > 1 && !clinicalBoilerplate(text)) {
                all += OcrLine(
                    text = text,
                    block = blockIndex,
                    top = box?.top ?: Int.MAX_VALUE / 4,
                    left = box?.left ?: 0,
                    bottom = box?.bottom ?: (box?.top ?: 0),
                    right = box?.right ?: (box?.left ?: 0)
                )
            }
        }
    }
    return all.sortedWith(compareBy<OcrLine> { it.top }.thenBy { it.left })
}'''
if old in s:
    s = s.replace(old, new)

start = s.index('private fun findResult(window: List<OcrLine>, def: ClinicalMarkerDef): ValueHit? {')
end = s.index('\nprivate fun findRange(', start)
new_find = r'''private fun findResult(window: List<OcrLine>, def: ClinicalMarkerDef): ValueHit? {
    val numberRx = Regex("(?<![A-Za-z])([<>]=?\\s*)?(-?\\d+(?:[.,]\\d+)?)(?![A-Za-z])")
    if (window.isEmpty()) return null
    val heading = window.first()
    var best: ValueHit? = null
    var bestScore = -1.0

    for (offset in window.indices) {
        val raw = window[offset].text
        if (offset > 0 && markerDef(raw)?.let { headingLooksReal(raw, it) } == true) break
        if (looksLikeRangeLine(raw) || isNhsSectionBoundary(raw) || clinicalBoilerplate(raw)) continue
        val candidate = if (offset == 0) stripAliases(raw, def.aliases) else raw
        if (Regex("(?:reference|normal|range|date|time|page)", RegexOption.IGNORE_CASE).containsMatchIn(candidate)) continue

        numberRx.findAll(candidate).forEach { hit ->
            val v = hit.groupValues[2].replace(',', '.')
            val number = v.toDoubleOrNull() ?: return@forEach
            if (looksLikeDateNumber(candidate, hit.range.first) || abs(number) > 1_000_000) return@forEach

            var score = 0.44
            score += when (offset) { 0 -> .12; 1 -> .26; 2 -> .22; 3 -> .15; else -> .05 }
            if (window[offset].block == heading.block) score += .08
            val verticalGap = (window[offset].top - heading.bottom).coerceAtLeast(0)
            if (verticalGap < 180) score += .08 else if (verticalGap > 700) score -= .10

            val unit = findUnit(raw, def.unit)
            if (unit.first.isNotBlank()) score += if (unitsCompatible(unit.first, def.unit)) .18 else -.18
            if (def.unit.isNotBlank() && unitRegexFor(def.unit).containsMatchIn(raw.replace(" ", ""))) score += .08
            if (candidate.trim().matches(Regex("^[<>]?=?\\s*-?\\d+(?:[.,]\\d+)?(?:\\s*[^0-9]+)?$"))) score += .07

            val comparator = hit.groupValues[1].replace(" ", "").trim()
            if (score > bestScore) {
                bestScore = score
                best = ValueHit(v, offset, score.coerceIn(.0, .99), comparator)
            }
        }
    }
    return best?.takeIf { bestScore >= .62 }
}'''
s = s[:start] + new_find + s[end:]

marker = 'private fun findRange(text: String): RangeHit {'
idx = s.index(marker)
helpers = r'''private fun normalizeClinicalUnitForCompare(unit: String): String = unit
    .trim().lowercase(Locale.ROOT)
    .replace("μ", "µ")
    .replace("²", "2").replace("^", "")
    .replace(Regex("\\s+"), "")
    .replace("umol/", "µmol/")
    .replace("ug/", "µg/")

private fun unitsCompatible(read: String, expected: String): Boolean {
    if (read.isBlank() || expected.isBlank()) return true
    return normalizeClinicalUnitForCompare(read) == normalizeClinicalUnitForCompare(expected)
}

private fun extractionIsCoherent(def: ClinicalMarkerDef, valueHit: ValueHit, range: RangeHit, unitRead: Pair<String, Double>): Boolean {
    val value = valueHit.value.toDoubleOrNull() ?: return false
    val low = range.low.toDoubleOrNull()
    val high = range.high.toDoubleOrNull()
    if (low != null && high != null && low > high) return false
    if (unitRead.first.isNotBlank() && def.unit.isNotBlank() && !unitsCompatible(unitRead.first, def.unit) && unitRead.second >= .80) return false
    if (!value.isFinite() || abs(value) > 1_000_000) return false

    val refLow = def.defaultLow.toDoubleOrNull() ?: def.maleLow.toDoubleOrNull() ?: def.femaleLow.toDoubleOrNull()
    val refHigh = def.defaultHigh.toDoubleOrNull() ?: def.maleHigh.toDoubleOrNull() ?: def.femaleHigh.toDoubleOrNull()
    if (refHigh != null && refHigh > 0 && abs(value) > max(1_000_000.0, abs(refHigh) * 10_000.0)) return false
    if (refLow != null && refHigh != null && low != null && high != null) {
        val knownSpan = abs(refHigh - refLow).coerceAtLeast(0.000001)
        if (abs(high - low) > knownSpan * 10_000) return false
    }
    return valueHit.confidence >= .62
}

'''
if 'private fun extractionIsCoherent(' not in s:
    s = s[:idx] + helpers + s[idx:]

old_parse = '''        val range = findRange(blockText)
        val unitRead = findUnit(blockText, def.unit)
        val confidence = (valueHit.confidence + markerConfidence(heading.text, def) + if (range.low.isNotBlank() || range.high.isNotBlank()) .08 else 0.0 + if (unitRead.first.isNotBlank()) .05 else 0.0).coerceIn(.0, .99)

        val draft = ClinicalDraft('''
new_parse = '''        val range = findRange(blockText)
        val unitRead = findUnit(blockText, def.unit)
        if (!extractionIsCoherent(def, valueHit, range, unitRead)) continue
        val markerScore = markerConfidence(heading.text, def)
        val unitScore = when {
            unitRead.first.isBlank() -> if (def.unit.isNotBlank()) .55 else .35
            unitsCompatible(unitRead.first, def.unit) -> unitRead.second
            else -> .25
        }
        val rangeScore = if (range.low.isNotBlank() || range.high.isNotBlank()) .92 else .55
        val confidence = (valueHit.confidence * .38 + markerScore * .34 + unitScore * .16 + rangeScore * .12).coerceIn(.0, .99)
        if (confidence < .64) continue

        val draft = ClinicalDraft('''
if old_parse in s:
    s = s.replace(old_parse, new_parse)

s = s.replace('"U/L" to Regex("U/L", RegexOption.IGNORE_CASE)', '"U/L" to Regex("(?<![A-Za-z])U/L", RegexOption.IGNORE_CASE)')

p.write_text(s)
