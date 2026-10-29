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

    fun romanToInt(raw: String): Int? {
        val clean = raw.trim().uppercase()
        val direct = when (clean) {
            "I" -> 1
            "II" -> 2
            "III" -> 3
            "IV" -> 4
            "V" -> 5
            "VI" -> 6
            "VII" -> 7
            "VIII" -> 8
            "IX" -> 9
            "X" -> 10
            else -> clean.toIntOrNull()
        }
        if (direct != null && direct in 1..10) return direct

        val match = Regex("""(?:SEMESTER|TERM|SEM)?\s*[:\-]?\s*(VIII|VII|III|VI|IV|IX|II|V|I|X|\d+)""").find(clean)
        if (match != null) {
            val token = match.groupValues[1]
            val tokenNum = when (token) {
                "I" -> 1
                "II" -> 2
                "III" -> 3
                "IV" -> 4
                "V" -> 5
                "VI" -> 6
                "VII" -> 7
                "VIII" -> 8
                "IX" -> 9
                "X" -> 10
                else -> token.toIntOrNull()
            }
            if (tokenNum != null && tokenNum in 1..10) return tokenNum
        }
        return null
    }

    fun intToRoman(n: Int): String {
        return when (n) {
            1 -> "I"
            2 -> "II"
            3 -> "III"
            4 -> "IV"
            5 -> "V"
            6 -> "VI"
            7 -> "VII"
            8 -> "VIII"
            9 -> "IX"
            10 -> "X"
            else -> n.toString()
        }
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
                    val rawCgpa = p.optDouble("cgpa", p.optDouble("CGPA", Double.NaN))
                    if (!rawCgpa.isNaN() && rawCgpa in 1.0..10.0) {
                        cgpa = rawCgpa
                    }
                }
                if (cgpa == null) {
                    val rawCgpa = obj.optDouble("cgpa", obj.optDouble("CGPA", Double.NaN))
                    if (!rawCgpa.isNaN() && rawCgpa in 1.0..10.0) {
                        cgpa = rawCgpa
                    }
                }

                // 2. TGPA Data check (from Next.js getStudentTgpaAction console hook)
                val tgpaData = obj.opt("tgpaData")
                if (tgpaData is JSONArray) {
                    for (i in 0 until tgpaData.length()) {
                        val row = tgpaData.optJSONObject(i) ?: continue
                        val cat = row.optString("Category", row.optString("category", "")).trim()

                        // Strictly ignore university / programme / batch / class averages
                        val isAverage = cat.contains("average", ignoreCase = true) ||
                                cat.contains("university", ignoreCase = true) ||
                                cat.contains("programme", ignoreCase = true) ||
                                cat.contains("program", ignoreCase = true) ||
                                cat.contains("batch", ignoreCase = true) ||
                                cat.contains("section", ignoreCase = true) ||
                                cat.contains("class", ignoreCase = true)

                        if (isAverage) continue

                        val isStudent = cat.equals("Student", ignoreCase = true) ||
                                cat.equals("Self", ignoreCase = true) ||
                                cat.equals("My Performance", ignoreCase = true) ||
                                cat.equals("Individual", ignoreCase = true) ||
                                cat.isBlank() ||
                                tgpaData.length() == 1

                        if (isStudent) {
                            val keys = row.keys()
                            while (keys.hasNext()) {
                                val k = keys.next()
                                if (k.startsWith("TGPASem", ignoreCase = true) || k.startsWith("Sem", ignoreCase = true)) {
                                    val semNum = k.replace(Regex("[^0-9]"), "").toIntOrNull() ?: continue
                                    val tgpaVal = row.optDouble(k, Double.NaN)
                                    if (!tgpaVal.isNaN() && tgpaVal in 1.0..10.0) {
                                        val existingIdx = terms.indexOfFirst { it.semester == semNum }
                                        val newTerm = ParsedTerm(
                                            name = "Semester : ${intToRoman(semNum)}",
                                            semester = semNum,
                                            tgpa = tgpaVal,
                                            totalCredits = 20.0
                                        )
                                        if (existingIdx >= 0) {
                                            terms[existingIdx] = newTerm
                                        } else {
                                            terms.add(newTerm)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 3. Check CoursewiseGrade, coursewiseList, domCourses, or terms inside object
                val courseArrays = mutableListOf<JSONArray>()
                obj.optJSONArray("coursewise")?.let { courseArrays.add(it) }
                obj.optJSONArray("coursewiseList")?.let { courseArrays.add(it) }
                obj.optJSONArray("CoursewiseGrade")?.let { courseArrays.add(it) }
                obj.optJSONArray("terms")?.let { courseArrays.add(it) }

                val objKeys = obj.keys()
                while (objKeys.hasNext()) {
                    val k = objKeys.next()
                    if (k.startsWith("domCourses_") || k.startsWith("fetched_") || k.startsWith("coursewise_")) {
                        obj.optJSONArray(k)?.let { courseArrays.add(it) }
                    }
                }

                // Also search nextData or nested objects recursively
                obj.optJSONObject("nextData")?.let { findCourseArraysRecursively(it, courseArrays) }

                for (arr in courseArrays) {
                    val parsedCoursewise = parseTermsArray(arr)
                    for (pt in parsedCoursewise) {
                        val existing = terms.firstOrNull { it.semester == pt.semester || it.name.equals(pt.name, ignoreCase = true) }
                        if (existing != null) {
                            val idx = terms.indexOf(existing)
                            val mergedSubs = (existing.subjects + pt.subjects).distinctBy { it.courseCode.uppercase() }
                            terms[idx] = existing.copy(
                                tgpa = existing.tgpa ?: pt.tgpa,
                                subjects = if (mergedSubs.isNotEmpty()) mergedSubs else existing.subjects,
                                totalCredits = if (mergedSubs.isNotEmpty() && mergedSubs.sumOf { it.credits } > 0.0) mergedSubs.sumOf { it.credits } else existing.totalCredits
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
        val termMap = mutableMapOf<Int, ParsedTerm>()

        for (i in 0 until arr.length()) {
            val item = arr.optJSONObject(i) ?: continue

            val coursesArr = item.optJSONArray("Courses")
                ?: item.optJSONArray("courses")
                ?: item.optJSONArray("Subjects")
                ?: item.optJSONArray("subjects")

            if (coursesArr != null) {
                var sem = item.optInt("Semester", item.optInt("semester", item.optInt("sem", item.optInt("Sem", item.optInt("term", 0)))))
                if (sem == 0) {
                    val tid = item.optInt("TermId", item.optInt("termId", 0))
                    if (tid > 0) sem = if (tid > 1000) (tid % 10) else tid
                }
                if (sem == 0) {
                    val tTag = item.optString("TermTag", item.optString("termTag", item.optString("TermName", "")))
                    sem = romanToInt(tTag) ?: (i + 1)
                }

                val termName = item.optString("TermTag", item.optString("termTag", "Semester : ${intToRoman(sem)}")).ifBlank { "Semester : ${intToRoman(sem)}" }
                val tgpaVal = item.optDouble("Tgpa", item.optDouble("tgpa", item.optDouble("TGPA", Double.NaN)))
                val parsedTgpa = tgpaVal.takeIf { !it.isNaN() && it in 1.0..10.0 }

                val subjects = mutableListOf<GpaSubject>()
                for (j in 0 until coursesArr.length()) {
                    val cObj = coursesArr.optJSONObject(j) ?: continue
                    val code = cObj.optString("CourseCode", cObj.optString("courseCode", cObj.optString("code", ""))).trim().uppercase()
                    if (code.isBlank()) continue
                    val title = cObj.optString("CourseTitle", cObj.optString("courseTitle", cObj.optString("courseName", ""))).trim()
                    val cr = cObj.optDouble("Credits", cObj.optDouble("credits", cObj.optDouble("credit", 0.0)))
                    val gr = cObj.optString("Grade", cObj.optString("grade", "")).trim().uppercase().ifBlank { null }

                    subjects.add(
                        GpaSubject(
                            courseCode = code,
                            courseTitle = title,
                            credits = if (cr > 0.0) cr else 0.0,
                            selectedGrade = gr,
                            isCustom = false,
                            userEditedCredits = false
                        )
                    )
                }

                val existing = termMap[sem]
                val mergedSubjects = ( (existing?.subjects ?: emptyList()) + subjects ).distinctBy { it.courseCode.uppercase() }
                val totalCr = if (mergedSubjects.isNotEmpty() && mergedSubjects.sumOf { it.credits } > 0.0) mergedSubjects.sumOf { it.credits } else item.optDouble("totalCredits", existing?.totalCredits ?: 20.0)

                termMap[sem] = ParsedTerm(
                    name = termName,
                    semester = sem,
                    tgpa = parsedTgpa ?: existing?.tgpa,
                    totalCredits = totalCr,
                    subjects = mergedSubjects
                )
            } else if (item.has("CourseCode") || item.has("courseCode") || item.has("code")) {
                val code = item.optString("CourseCode", item.optString("courseCode", item.optString("code", ""))).trim().uppercase()
                if (code.isNotBlank()) {
                    val title = item.optString("CourseTitle", item.optString("courseTitle", item.optString("courseName", ""))).trim()
                    val cr = item.optDouble("Credits", item.optDouble("credits", item.optDouble("credit", 0.0)))
                    val gr = item.optString("Grade", item.optString("grade", "")).trim().uppercase().ifBlank { null }

                    var sem = item.optInt("Semester", item.optInt("semester", item.optInt("sem", item.optInt("Sem", item.optInt("term", 0)))))
                    if (sem == 0) {
                        val tid = item.optInt("TermId", item.optInt("termId", 0))
                        if (tid > 0) sem = if (tid > 1000) (tid % 10) else tid
                    }
                    if (sem == 0) {
                        val tTag = item.optString("TermTag", item.optString("termTag", item.optString("TermName", item.optString("termName", ""))))
                        sem = romanToInt(tTag) ?: 0
                    }
                    // If still 0, check specific course signature: CSE310, CSE316, INT222, INT428 are known Semester IV
                    if (sem == 0) {
                        sem = when (code) {
                            "CSE211", "CSE310", "CSE316", "CSE325", "INT219", "INT222", "INT428", "PEA305" -> 4
                            else -> 1
                        }
                    }

                    val existing = termMap[sem]
                    val currentSubs = (existing?.subjects ?: emptyList()).toMutableList()
                    val existingSubIdx = currentSubs.indexOfFirst { it.courseCode.equals(code, ignoreCase = true) }
                    val newSubject = GpaSubject(
                        courseCode = code,
                        courseTitle = title,
                        credits = if (cr > 0.0) cr else 0.0,
                        selectedGrade = gr,
                        isCustom = false,
                        userEditedCredits = false
                    )
                    if (existingSubIdx >= 0) {
                        currentSubs[existingSubIdx] = newSubject
                    } else {
                        currentSubs.add(newSubject)
                    }

                    termMap[sem] = ParsedTerm(
                        name = "Semester : ${intToRoman(sem)}",
                        semester = sem,
                        tgpa = existing?.tgpa,
                        totalCredits = if (currentSubs.sumOf { it.credits } > 0.0) currentSubs.sumOf { it.credits } else 20.0,
                        subjects = currentSubs
                    )
                }
            }
        }
        return termMap.values.sortedBy { it.semester ?: 99 }
    }

    private fun parseFromDomText(text: String): ParsedResultSummary {
        var cgpa: Double? = null
        val terms = mutableListOf<ParsedTerm>()

        // 1. CGPA Extraction
        // Match number before CGPA / Overall Performance (e.g. "8.31\nOverall Performance" or "8.31 CGPA")
        val cgpaNumFirstPattern = Pattern.compile(
            """([0-9]+\.[0-9]+)\s*(?:\n|\r|\s)+\s*(?:Overall\s*Performance|CGPA|NET\s*PERCENTAGE|Cumulative\s*Grade)""",
            Pattern.CASE_INSENSITIVE
        )
        val m1 = cgpaNumFirstPattern.matcher(text)
        if (m1.find()) {
            val v = m1.group(1)?.toDoubleOrNull()
            if (v != null && v in 1.0..10.0) {
                cgpa = v
            }
        }

        // Match CGPA / Overall Performance before number (e.g. "Overall Performance\n8.31" or "CGPA: 8.31")
        if (cgpa == null) {
            val cgpaPattern = Pattern.compile(
                """(?:Overall\s*Performance|CGPA|Cumulative\s*Grade\s*Point\s*Average|Net\s*Percentage)\s*[:\-]?\s*(?:\n|\r|\s)*\s*([0-9]+\.[0-9]+)""",
                Pattern.CASE_INSENSITIVE
            )
            val m2 = cgpaPattern.matcher(text)
            if (m2.find()) {
                val v = m2.group(1)?.toDoubleOrNull()
                if (v != null && v in 1.0..10.0) {
                    cgpa = v
                }
            }
        }

        val foundTerms = mutableMapOf<Int, Double>()

        // 2. Term & TGPA Extraction (allowing Roman numerals: I, II, III, IV, etc.)
        // Pattern A: "Semester : I \n 7.77" or "Semester I \n 7.77" or "Term 1 \n 7.77"
        val semPatternA = Pattern.compile(
            """(?:Semester|Term)\s*[:\-]?\s*\b(VIII|VII|III|VI|IV|IX|II|V|I|X|[0-9]+)\b[\s\S]{0,100}?(?:TGPA|Term\s*GPA|Grade\s*Point\s*Average)?\s*[:\-]?\s*([0-9]+\.[0-9]{1,2})""",
            Pattern.CASE_INSENSITIVE
        )
        val matcherA = semPatternA.matcher(text)
        while (matcherA.find()) {
            val semStr = matcherA.group(1) ?: continue
            val sem = romanToInt(semStr) ?: continue
            val tgpa = matcherA.group(2)?.toDoubleOrNull() ?: continue
            if (tgpa in 1.0..10.0) {
                foundTerms[sem] = tgpa
            }
        }

        // Pattern B (reversed): "7.77 \n Semester : I"
        if (foundTerms.isEmpty()) {
            val semPatternB = Pattern.compile(
                """([0-9]+\.[0-9]{1,2})[\s\S]{0,100}?(?:Semester|Term)\s*[:\-]?\s*\b(VIII|VII|III|VI|IV|IX|II|V|I|X|[0-9]+)\b""",
                Pattern.CASE_INSENSITIVE
            )
            val matcherB = semPatternB.matcher(text)
            while (matcherB.find()) {
                val tgpa = matcherB.group(1)?.toDoubleOrNull() ?: continue
                val semStr = matcherB.group(2) ?: continue
                val sem = romanToInt(semStr) ?: continue
                if (tgpa in 1.0..10.0 && !foundTerms.containsKey(sem)) {
                    foundTerms[sem] = tgpa
                }
            }
        }

        // Fallback for chips "Term 1: 8.40"
        if (foundTerms.isEmpty()) {
            val chipPattern = Pattern.compile("""(?:Term|Semester)\s*([0-9]+)\s*[:\-]\s*([0-9]+\.[0-9]+)""", Pattern.CASE_INSENSITIVE)
            val chipMatcher = chipPattern.matcher(text)
            while (chipMatcher.find()) {
                val sem = chipMatcher.group(1)?.toIntOrNull() ?: continue
                val tgpa = chipMatcher.group(2)?.toDoubleOrNull() ?: continue
                if (tgpa in 1.0..10.0) {
                    foundTerms[sem] = tgpa
                }
            }
        }

        for ((sem, tgpa) in foundTerms.toSortedMap()) {
            terms.add(
                ParsedTerm(
                    name = "Semester : ${intToRoman(sem)}",
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

        val cgpaMatch = Regex("""(?:CGPA|Cumulative\s*Grade|Overall\s*Performance)[^<0-9]*([0-9]+(?:\.[0-9]+)?)""", RegexOption.IGNORE_CASE).find(html)
        if (cgpaMatch != null) {
            val v = cgpaMatch.groupValues[1].toDoubleOrNull()
            if (v != null && v in 1.0..10.0) {
                cgpa = v
            }
        }

        // 1. Classic TermId / TGPA pattern from frmStudentResult.aspx: "<p>TermId: 124251; TGPA: 8.14</p>"
        val classicTgpaMap = mutableMapOf<String, Double>()
        val termTgpaRegex = Regex("""TermId:\s*([0-9A-Za-z]+).*?TGPA:\s*([0-9]+(?:\.[0-9]+)?)""", RegexOption.IGNORE_CASE)
        for (m in termTgpaRegex.findAll(html)) {
            val tid = m.groupValues[1].trim()
            val tgpa = m.groupValues[2].toDoubleOrNull() ?: continue
            if (tgpa in 1.0..10.0) {
                classicTgpaMap[tid] = tgpa
            }
        }

        // Regular terms are sorted in ascending order (Semester 1 = earliest digit ID)
        val regularTermIds = classicTgpaMap.keys
            .filter { it.matches(Regex("""^\d+$""")) }
            .sorted()

        var counter = 1
        for (tid in regularTermIds) {
            val tgpa = classicTgpaMap[tid] ?: continue
            terms.add(
                ParsedTerm(
                    name = "Semester : ${intToRoman(counter)}",
                    semester = counter,
                    tgpa = tgpa,
                    totalCredits = 20.0
                )
            )
            counter++
        }

        // 2. Fallback: match "Semester I: 7.77" or "Term 1: 7.77"
        if (terms.isEmpty()) {
            val tgpaMatches = Regex("""(?:Term|Semester)\s*[:\-]?\s*(I|II|III|IV|V|VI|VII|VIII|IX|X|[0-9]+)[^<0-9]*?TGPA[^<0-9]*([0-9]+(?:\.[0-9]+)?)""", RegexOption.IGNORE_CASE).findAll(html)
            for (m in tgpaMatches) {
                val sem = romanToInt(m.groupValues[1]) ?: continue
                val tgpa = m.groupValues[2].toDoubleOrNull() ?: continue
                if (tgpa in 1.0..10.0 && terms.none { it.semester == sem }) {
                    terms.add(
                        ParsedTerm(
                            name = "Semester : ${intToRoman(sem)}",
                            semester = sem,
                            tgpa = tgpa,
                            totalCredits = 20.0
                        )
                    )
                }
            }
        }

        // 3. Extract any <tr> rows with course code and grade from HTML tables
        val trRegex = Regex("""<tr[^>]*>([\s\S]*?)</tr>""", RegexOption.IGNORE_CASE)
        val tdRegex = Regex("""<td[^>]*>([\s\S]*?)</td>""", RegexOption.IGNORE_CASE)
        val tagStrip = Regex("""<[^>]*>""")

        for (m in trRegex.findAll(html)) {
            val rowContent = m.groupValues[1]
            val cells = tdRegex.findAll(rowContent).map { tagStrip.replace(it.groupValues[1], "").trim() }.toList()
            if (cells.size >= 3) {
                val codeCell = cells.firstOrNull { it.matches(Regex("""^[A-Z]{2,5}\d{3,4}$""")) } ?: continue
                val gradeCell = cells.firstOrNull { it.matches(Regex("""^(O|A\+|A|B\+|B|C|D|E|F|S|U)$""", RegexOption.IGNORE_CASE)) }
                val titleCell = cells.firstOrNull { it.length > 5 && it != codeCell && !it.matches(Regex("""^[0-9.]+$""")) } ?: ""
                val creditCell = cells.firstOrNull { it.matches(Regex("""^[0-9](\.[0-9])?$""")) }?.toDoubleOrNull() ?: 0.0

                if (gradeCell != null && terms.isNotEmpty()) {
                    val targetTerm = terms.firstOrNull { it.subjects.none { s -> s.courseCode.equals(codeCell, ignoreCase = true) } } ?: terms[0]
                    val idx = terms.indexOf(targetTerm)
                    val updatedSubs = (targetTerm.subjects + GpaSubject(
                        courseCode = codeCell,
                        courseTitle = titleCell,
                        credits = creditCell,
                        selectedGrade = gradeCell.uppercase()
                    )).distinctBy { it.courseCode.uppercase() }
                    terms[idx] = targetTerm.copy(subjects = updatedSubs)
                }
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

        // 1. Add all parsed past terms (Semester : I, Semester : II, etc.)
        for (pt in summary.terms.sortedBy { it.semester ?: 99 }) {
            val semNum = pt.semester
            val termId = if (semNum != null) "term_sem_$semNum" else "term_${pt.name.lowercase().replace(" ", "_")}"
            // Don't overwrite the current term with past term
            if (termId == currentTerm.id) continue

            val existing = currentState.terms.firstOrNull {
                it.id == termId ||
                (semNum != null && it.semesterNumber == semNum) ||
                it.name.equals(pt.name, ignoreCase = true)
            }
            val mergedSubjects = if (pt.subjects.isNotEmpty()) {
                (existing?.subjects.orEmpty() + pt.subjects).distinctBy { it.courseCode.uppercase() }
            } else {
                existing?.subjects ?: emptyList()
            }

            val termData = TermData(
                id = existing?.id ?: termId,
                name = pt.name,
                isCurrent = false,
                subjects = mergedSubjects,
                manualTgpa = pt.tgpa ?: existing?.manualTgpa,
                manualCredits = pt.totalCredits ?: existing?.manualCredits ?: 20.0,
                isCustom = false
            )
            updatedTerms.add(termData)
        }

        // 2. Add any existing past terms that weren't in the parsed summary
        for (et in currentState.terms) {
            val etSem = et.semesterNumber
            if (!et.isCurrent && updatedTerms.none { it.id == et.id || (etSem != null && it.semesterNumber == etSem) || it.name.equals(et.name, ignoreCase = true) }) {
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

    private fun findCourseArraysRecursively(json: Any, out: MutableList<JSONArray>, depth: Int = 0) {
        if (depth > 6) return
        when (json) {
            is JSONObject -> {
                val keys = json.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    val v = json.opt(k) ?: continue
                    if (v is JSONArray) {
                        if (v.length() > 0 && isCourseArray(v)) {
                            out.add(v)
                        } else {
                            findCourseArraysRecursively(v, out, depth + 1)
                        }
                    } else if (v is JSONObject) {
                        findCourseArraysRecursively(v, out, depth + 1)
                    }
                }
            }
            is JSONArray -> {
                for (i in 0 until json.length()) {
                    val item = json.opt(i) ?: continue
                    if (item is JSONObject || item is JSONArray) {
                        findCourseArraysRecursively(item, out, depth + 1)
                    }
                }
            }
        }
    }

    private fun isCourseArray(arr: JSONArray): Boolean {
        for (i in 0 until minOf(3, arr.length())) {
            val o = arr.optJSONObject(i) ?: continue
            if (o.has("CourseCode") || o.has("courseCode") || o.has("code") || o.has("CourseTitle") || o.has("courseTitle")) {
                return true
            }
        }
        return false
    }
}
