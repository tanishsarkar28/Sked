package com.sked.sked_app.gpa

import android.content.Context
import android.util.Log
import com.sked.sked_app.ClassItem
import org.json.JSONArray
import org.json.JSONObject
import java.util.regex.Pattern

data class ParsedTerm(
    val name: String,
    val semester: Int? = null,
    val tgpa: Double? = null,
    val totalCredits: Double? = null,
    val subjects: List<GpaSubject> = emptyList()
)

data class ParsedResultSummary(
    val cgpa: Double? = null,
    val terms: List<ParsedTerm> = emptyList(),
    val source: String = "ums"
)

object ResultParser {
    private const val TAG = "ResultParser"

    /**
     * Parses result summary from any available string payload:
     * - Next.js console hook JSON (CoursewiseGrade, TGPA Data, profileData)
     * - DOM text from studentums.lpu.in
     * - Classic UMS HTML tables
     */
    fun parse(rawInput: String): ParsedResultSummary {
        val clean = rawInput.trim()
        if (clean.isBlank()) return ParsedResultSummary()

        // 1. Try JSON parsing first (from console.log or API response)
        val jsonResult = parseFromJson(clean)
        if (jsonResult.terms.isNotEmpty() || jsonResult.cgpa != null) {
            Log.i(TAG, "Parsed ${jsonResult.terms.size} terms from JSON payload (CGPA: ${jsonResult.cgpa})")
            return jsonResult
        }

        // 2. Try DOM text parsing (from studentums.lpu.in innerText)
        val textResult = parseFromDomText(clean)
        if (textResult.terms.isNotEmpty() || textResult.cgpa != null) {
            Log.i(TAG, "Parsed ${textResult.terms.size} terms from DOM text (CGPA: ${textResult.cgpa})")
            return textResult
        }

        // 3. Try classic UMS HTML table parsing
        val htmlResult = parseFromClassicHtml(clean)
        if (htmlResult.terms.isNotEmpty() || htmlResult.cgpa != null) {
            Log.i(TAG, "Parsed ${htmlResult.terms.size} terms from Classic HTML (CGPA: ${htmlResult.cgpa})")
            return htmlResult
        }

        return ParsedResultSummary()
    }

    private fun parseFromJson(raw: String): ParsedResultSummary {
        var cgpa: Double? = null
        val terms = mutableListOf<ParsedTerm>()

        try {
            val startIdx = raw.indexOfAny(charArrayOf('[', '{'))
            if (startIdx == -1) return ParsedResultSummary()
            val candidate = raw.substring(startIdx)

            if (candidate.startsWith("{")) {
                val obj = JSONObject(candidate)

                // 1. Profile / CGPA check
                val profObj = obj.optJSONObject("profile") ?: obj
                val profArr = profObj.optJSONArray("profileData")
                if (profArr != null && profArr.length() > 0) {
                    val p = profArr.getJSONObject(0)
                    val rawCgpa = p.optDouble("cgpa", Double.NaN)
                    if (!rawCgpa.isNaN() && rawCgpa in 1.0..10.0) {
                        cgpa = rawCgpa
                    }
                }
                if (cgpa == null) {
                    val rawCgpa = obj.optDouble("cgpa", Double.NaN)
                    if (!rawCgpa.isNaN() && rawCgpa in 1.0..10.0) {
                        cgpa = rawCgpa
                    }
                }

                // 2. TGPA Data check (from Next.js getStudentTgpaAction console hook)
                val tgpaData = obj.opt("tgpaData")
                if (tgpaData is JSONArray) {
                    for (i in 0 until tgpaData.length()) {
                        val row = tgpaData.optJSONObject(i) ?: continue
                        val cat = row.optString("Category", "")
                        if (cat.equals("Student", ignoreCase = true) || tgpaData.length() == 1) {
                            val keys = row.keys()
                            while (keys.hasNext()) {
                                val k = keys.next()
                                if (k.startsWith("TGPASem", ignoreCase = true)) {
                                    val semNum = k.substring(7).toIntOrNull() ?: continue
                                    val tgpaVal = row.optDouble(k, Double.NaN)
                                    if (!tgpaVal.isNaN() && tgpaVal in 1.0..10.0) {
                                        terms.add(
                                            ParsedTerm(
                                                name = "Term $semNum",
                                                semester = semNum,
                                                tgpa = tgpaVal,
                                                totalCredits = 20.0
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 3. Check CoursewiseGrade or Terms inside object
                val coursewise = obj.optJSONArray("coursewise")
                    ?: obj.optJSONArray("CoursewiseGrade")
                    ?: obj.optJSONArray("terms")
                if (coursewise != null) {
                    val parsedCoursewise = parseTermsArray(coursewise)
                    for (pt in parsedCoursewise) {
                        val existing = terms.firstOrNull { it.semester == pt.semester || it.name.equals(pt.name, ignoreCase = true) }
                        if (existing != null) {
                            val idx = terms.indexOf(existing)
                            terms[idx] = existing.copy(
                                tgpa = existing.tgpa ?: pt.tgpa,
                                subjects = if (pt.subjects.isNotEmpty()) pt.subjects else existing.subjects,
                                totalCredits = if (pt.subjects.isNotEmpty()) pt.subjects.sumOf { it.credits } else existing.totalCredits
                            )
                        } else {
                            terms.add(pt)
                        }
                    }
                }
            } else if (candidate.startsWith("[")) {
                val arr = JSONArray(candidate)
                terms.addAll(parseTermsArray(arr))
            }
        } catch (e: Exception) {
            Log.w(TAG, "JSON parse notice: ${e.message}")
        }

        return ParsedResultSummary(cgpa = cgpa, terms = terms.sortedBy { it.semester ?: 99 }, source = "json")
    }

    private fun parseTermsArray(arr: JSONArray): List<ParsedTerm> {
        val result = mutableListOf<ParsedTerm>()
        for (i in 0 until arr.length()) {
            val item = arr.optJSONObject(i) ?: continue

            val sem = item.optInt("Semester", item.optInt("semester", i + 1))
            val termName = item.optString("TermTag", item.optString("termTag", "Term $sem")).ifBlank { "Term $sem" }
            val tgpaVal = item.optDouble("Tgpa", item.optDouble("tgpa", item.optDouble("TGPA", Double.NaN)))
            val parsedTgpa = tgpaVal.takeIf { !it.isNaN() && it in 1.0..10.0 }

            val subjects = mutableListOf<GpaSubject>()
            val coursesArr = item.optJSONArray("Courses")
                ?: item.optJSONArray("courses")
                ?: item.optJSONArray("Subjects")
                ?: item.optJSONArray("subjects")

            if (coursesArr != null) {
                for (j in 0 until coursesArr.length()) {
                    val cObj = coursesArr.optJSONObject(j) ?: continue
                    val code = cObj.optString("CourseCode", cObj.optString("courseCode", cObj.optString("code", ""))).trim().uppercase()
                    if (code.isBlank()) continue
                    val title = cObj.optString("CourseTitle", cObj.optString("courseTitle", cObj.optString("courseName", ""))).trim()
                    val cr = cObj.optDouble("Credits", cObj.optDouble("credits", cObj.optDouble("credit", 3.0)))
                    val gr = cObj.optString("Grade", cObj.optString("grade", "")).trim().uppercase().ifBlank { null }

                    subjects.add(
                        GpaSubject(
                            courseCode = code,
                            courseTitle = title,
                            credits = if (cr > 0.0) cr else 3.0,
                            selectedGrade = gr
                        )
                    )
                }
            }

            val totalCr = if (subjects.isNotEmpty()) subjects.sumOf { it.credits } else item.optDouble("totalCredits", 20.0)

            result.add(
                ParsedTerm(
                    name = termName,
                    semester = sem,
                    tgpa = parsedTgpa,
                    totalCredits = totalCr,
                    subjects = subjects
                )
            )
        }
        return result
    }

    private fun parseFromDomText(text: String): ParsedResultSummary {
        var cgpa: Double? = null
        val terms = mutableListOf<ParsedTerm>()

        // 1. CGPA Extraction
        // Match number before CGPA (e.g. "8.65\nCGPA" or "8.65 CGPA")
        val cgpaNumFirstPattern = Pattern.compile("""([0-9]+\.[0-9]+)\s*(?:\n|\r|\s)+\s*(?:CGPA|NET\s*PERCENTAGE)""", Pattern.CASE_INSENSITIVE)
        val m1 = cgpaNumFirstPattern.matcher(text)
        if (m1.find()) {
            val v = m1.group(1)?.toDoubleOrNull()
            if (v != null && v in 1.0..10.0) {
                cgpa = v
            }
        }

        // Match CGPA before number (e.g. "CGPA: 8.65")
        if (cgpa == null) {
            val cgpaPattern = Pattern.compile("""(?:CGPA|Cumulative\s*Grade\s*Point\s*Average|Net\s*Percentage)\s*[:\-]?\s*([0-9]+\.[0-9]+)""", Pattern.CASE_INSENSITIVE)
            val m2 = cgpaPattern.matcher(text)
            if (m2.find()) {
                val v = m2.group(1)?.toDoubleOrNull()
                if (v != null && v in 1.0..10.0) {
                    cgpa = v
                }
            }
        }

        // 2. Term & TGPA Extraction (allowing newlines between Term and TGPA)
        val termPattern = Pattern.compile(
            """Term\s*([0-9]+)[\s\S]{0,100}?(?:TGPA|Term\s*GPA|Grade\s*Point\s*Average)\s*[:\-]?\s*([0-9]+\.[0-9]+)""",
            Pattern.CASE_INSENSITIVE
        )
        val termMatcher = termPattern.matcher(text)
        val foundTerms = mutableMapOf<Int, Double>()
        while (termMatcher.find()) {
            val sem = termMatcher.group(1)?.toIntOrNull() ?: continue
            val tgpa = termMatcher.group(2)?.toDoubleOrNull() ?: continue
            if (tgpa in 1.0..10.0) {
                foundTerms[sem] = tgpa
            }
        }

        // Fallback for chips "Term 1: 8.40"
        if (foundTerms.isEmpty()) {
            val chipPattern = Pattern.compile("""Term\s*([0-9]+)\s*[:\-]\s*([0-9]+\.[0-9]+)""", Pattern.CASE_INSENSITIVE)
            val chipMatcher = chipPattern.matcher(text)
            while (chipMatcher.find()) {
                val sem = chipMatcher.group(1)?.toIntOrNull() ?: continue
                val tgpa = chipMatcher.group(2)?.toDoubleOrNull() ?: continue
                if (tgpa in 1.0..10.0) {
                    foundTerms[sem] = tgpa
                }
            }
        }

        // Fallback for reversed pattern "TGPA: 8.40 ... Term 1"
        if (foundTerms.isEmpty()) {
            val revPattern = Pattern.compile(
                """(?:TGPA|Term\s*GPA)\s*[:\-]?\s*([0-9]+\.[0-9]+)[\s\S]{0,100}?Term\s*([0-9]+)""",
                Pattern.CASE_INSENSITIVE
            )
            val revMatcher = revPattern.matcher(text)
            while (revMatcher.find()) {
                val tgpa = revMatcher.group(1)?.toDoubleOrNull() ?: continue
                val sem = revMatcher.group(2)?.toIntOrNull() ?: continue
                if (tgpa in 1.0..10.0) {
                    foundTerms[sem] = tgpa
                }
            }
        }

        for ((sem, tgpa) in foundTerms.toSortedMap()) {
            terms.add(
                ParsedTerm(
                    name = "Term $sem",
                    semester = sem,
                    tgpa = tgpa,
                    totalCredits = 20.0
                )
            )
        }

        return ParsedResultSummary(cgpa = cgpa, terms = terms, source = "dom_text")
    }

    private fun parseFromClassicHtml(html: String): ParsedResultSummary {
        var cgpa: Double? = null
        val terms = mutableListOf<ParsedTerm>()

        val cgpaMatch = Regex("""(?:CGPA|Cumulative\s*Grade)[^<0-9]*([0-9]+(?:\.[0-9]+)?)""", RegexOption.IGNORE_CASE).find(html)
        if (cgpaMatch != null) {
            val v = cgpaMatch.groupValues[1].toDoubleOrNull()
            if (v != null && v in 1.0..10.0) {
                cgpa = v
            }
        }

        val tgpaMatches = Regex("""(?:Term|Semester)\s*([0-9]+)[^<]*?TGPA[^<0-9]*([0-9]+(?:\.[0-9]+)?)""", RegexOption.IGNORE_CASE).findAll(html)
        for (m in tgpaMatches) {
            val sem = m.groupValues[1].toIntOrNull() ?: continue
            val tgpa = m.groupValues[2].toDoubleOrNull() ?: continue
            if (tgpa in 1.0..10.0 && terms.none { it.semester == sem }) {
                terms.add(
                    ParsedTerm(
                        name = "Term $sem",
                        semester = sem,
                        tgpa = tgpa,
                        totalCredits = 20.0
                    )
                )
            }
        }

        return ParsedResultSummary(cgpa = cgpa, terms = terms, source = "classic_html")
    }

    /**
     * Merges parsed result summary into current GpaState and persists to SharedPreferences.
     * Preserves the active Current Term (from timetable) while updating past semesters & CGPA.
     */
    fun mergeIntoStorage(context: Context, summary: ParsedResultSummary, timetableEntries: List<ClassItem>): GpaState {
        val currentState = GpaStorage.load(context, timetableEntries)
        val currentTerm = currentState.terms.firstOrNull { it.isCurrent }
            ?: TermData(id = "current_term", name = "Current Term", isCurrent = true)

        val updatedTerms = mutableListOf<TermData>()

        // 1. Add all parsed past terms (Term 1, Term 2, etc.)
        for (pt in summary.terms.sortedBy { it.semester ?: 99 }) {
            val termId = "term_${pt.name.lowercase().replace(" ", "_")}"
            // Don't overwrite the current term with past term
            if (termId == currentTerm.id) continue

            val existing = currentState.terms.firstOrNull { it.id == termId || it.name.equals(pt.name, ignoreCase = true) }
            val mergedSubjects = if (pt.subjects.isNotEmpty()) {
                pt.subjects
            } else {
                existing?.subjects ?: emptyList()
            }

            val termData = TermData(
                id = termId,
                name = pt.name,
                isCurrent = false,
                subjects = mergedSubjects,
                manualTgpa = pt.tgpa ?: existing?.manualTgpa,
                manualCredits = pt.totalCredits ?: existing?.manualCredits ?: 20.0
            )
            updatedTerms.add(termData)
        }

        // 2. Add any existing past terms that weren't in the parsed summary
        for (et in currentState.terms) {
            if (!et.isCurrent && updatedTerms.none { it.id == et.id || it.name.equals(et.name, ignoreCase = true) }) {
                updatedTerms.add(et)
            }
        }

        // 3. Append the Current Term (active semester from timetable) at the end
        updatedTerms.add(currentTerm)

        val validCgpa = summary.cgpa?.takeIf { it in 1.0..10.0 }
        val newState = currentState.copy(
            terms = updatedTerms,
            activeTermId = currentTerm.id,
            officialCgpa = validCgpa ?: currentState.officialCgpa
        )

        GpaStorage.save(context, newState)
        Log.i(TAG, "Merged result summary: ${newState.terms.size} total terms (CGPA: ${newState.officialCgpa ?: newState.cumulativeCgpa})")
        return newState
    }
}
