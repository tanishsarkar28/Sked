import WidgetKit
import SwiftUI

// ── Color Palette ─────────────────────────────────────────────────────────────
extension Color {
    static let skedInk = Color(red: 10/255, green: 10/255, blue: 10/255)
    static let skedSlab = Color(red: 20/255, green: 20/255, blue: 20/255)
    static let skedSlabElevated = Color(red: 26/255, green: 26/255, blue: 26/255)
    static let skedRule = Color(red: 37/255, green: 37/255, blue: 37/255)
    static let skedChalk = Color(red: 232/255, green: 230/255, blue: 227/255)
    static let skedSlate = Color(red: 122/255, green: 119/255, blue: 116/255)
    static let skedBlaze = Color(red: 255/255, green: 107/255, blue: 26/255)
    static let skedGreen = Color(red: 16/255, green: 185/255, blue: 129/255)
    static let skedDim = Color(red: 90/255, green: 88/255, blue: 86/255)
}

// ── Data Models ───────────────────────────────────────────────────────────────
struct ClassItem: Codable, Identifiable {
    var id: String { "\(courseCode)_\(start)" }
    let courseCode: String
    let start: String
    let end: String
    let room: String
    let type: String
    let timeRange: String
    let teacher: String
    let section: String

    var startMinutes: Int {
        let parts = start.split(separator: ":")
        guard parts.count >= 2,
              let h = Int(parts[0]),
              let m = Int(parts[1].prefix(2)) else { return -1 }
        return h * 60 + m
    }

    var endMinutes: Int {
        let cleanEnd = end.isEmpty ? start : end
        let parts = cleanEnd.split(separator: ":")
        guard parts.count >= 2,
              let h = Int(parts[0]),
              let m = Int(parts[1].prefix(2)) else { return -1 }
        return h * 60 + m
    }

    var shortType: String {
        switch type {
        case "Lecture": return "LEC"
        case "Practical": return "PRAC"
        case "Tutorial": return "TUT"
        default: return String(type.prefix(3)).uppercased()
        }
    }
}

struct ExamData: Codable {
    let courseCode: String
    let courseTitle: String
    let timeSlot: String
    let session: String
    let examType: String
    let room: String
    let seatNo: String
    let reportingTime: String
    let isToday: Bool
}

// ── Timeline Entry ────────────────────────────────────────────────────────────
struct TimetableEntry: TimelineEntry {
    let date: Date
    let classes: [ClassItem]
    let isSunday: Bool
    let exam: ExamData?

    var currentMinutes: Int {
        let cal = Calendar.current
        return cal.component(.hour, from: date) * 60 + cal.component(.minute, from: date)
    }

    var liveClass: ClassItem? {
        let now = currentMinutes
        return classes.first { c in
            c.startMinutes > 0 && c.endMinutes > 0 && (c.startMinutes...c.endMinutes).contains(now)
        }
    }

    var nextClass: ClassItem? {
        let now = currentMinutes
        return classes.first { c in
            c.startMinutes > 0 && c.startMinutes > now
        }
    }

    var completedCount: Int {
        let now = currentMinutes
        return classes.filter { c in
            c.endMinutes > 0 && now > c.endMinutes
        }.count
    }

    var allDone: Bool {
        !classes.isEmpty && completedCount >= classes.sizeCount
    }
}

extension Array {
    var sizeCount: Int { count }
}

// ── Timeline Provider ─────────────────────────────────────────────────────────
struct Provider: TimelineProvider {
    func placeholder(in context: Context) -> TimetableEntry {
        TimetableEntry(
            date: Date(),
            classes: [
                ClassItem(courseCode: "CSE330", start: "09:30", end: "10:20", room: "34-706", type: "PRAC", timeRange: "09:30 – 10:20", teacher: "Ashish Shivhare", section: "G:0"),
                ClassItem(courseCode: "INT252", start: "10:20", end: "11:10", room: "37-902", type: "LEC", timeRange: "10:20 – 11:10", teacher: "Dr. Navneet Kaur", section: "G:0")
            ],
            isSunday: false,
            exam: nil
        )
    }

    func getSnapshot(in context: Context, completion: @escaping (TimetableEntry) -> Void) {
        completion(loadData(for: Date()))
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<TimetableEntry>) -> Void) {
        let entry = loadData(for: Date())
        // Refresh every 15 minutes or at next class start
        let nextUpdate = Calendar.current.date(byAdding: .minute, value: 15, to: Date()) ?? Date()
        let timeline = Timeline(entries: [entry], policy: .after(nextUpdate))
        completion(timeline)
    }

    private func loadData(for date: Date) -> TimetableEntry {
        let defaults = UserDefaults(suiteName: "group.com.sked.skedApp") ?? UserDefaults.standard

        var loadedClasses: [ClassItem] = []
        if let jsonStr = defaults.string(forKey: "today_entries"),
           let data = jsonStr.data(using: .utf8) {
            do {
                loadedClasses = try JSONDecoder().decode([ClassItem].self, from: data)
            } catch {}
        }

        var loadedExam: ExamData? = nil
        if let examStr = defaults.string(forKey: "today_exam"),
           let data = examStr.data(using: .utf8) {
            do {
                loadedExam = try JSONDecoder().decode(ExamData.self, from: data)
            } catch {}
        }

        let cal = Calendar.current
        let isSundayCal = cal.component(.weekday, from: date) == 1
        let isSundaySaved = defaults.bool(forKey: "is_sunday")
        let isSunday = isSundayCal || isSundaySaved

        return TimetableEntry(
            date: date,
            classes: loadedClasses,
            isSunday: isSunday,
            exam: loadedExam
        )
    }
}

// ── Widget Views ──────────────────────────────────────────────────────────────
struct TimetableWidgetEntryView: View {
    var entry: Provider.Entry
    @Environment(\.widgetFamily) var family

    var body: some View {
        ZStack {
            Color.skedInk.ignoresSafeArea()

            switch family {
            case .systemSmall:
                SmallWidgetView(entry: entry)
            default:
                MediumWidgetView(entry: entry)
            }
        }
    }
}

// ── Small Widget ──────────────────────────────────────────────────────────────
struct SmallWidgetView: View {
    let entry: Provider.Entry

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            // Header
            HStack {
                Text("SKED.")
                    .font(.system(size: 13, weight: .black, design: .monospaced))
                    .foregroundColor(.skedBlaze)
                Spacer()
                Text(entry.date, style: .time)
                    .font(.system(size: 10, weight: .bold, design: .monospaced))
                    .foregroundColor(.skedSlate)
            }

            Spacer()

            if let exam = entry.exam {
                // Exam State
                VStack(alignment: .leading, spacing: 3) {
                    Text(exam.isToday ? "EXAM TODAY." : "EXAM TOMORROW.")
                        .font(.system(size: 9, weight: .bold))
                        .foregroundColor(.skedBlaze)
                    Text(exam.courseCode)
                        .font(.system(size: 18, weight: .black, design: .rounded))
                        .foregroundColor(.skedChalk)
                    if !exam.room.isEmpty && exam.room != "Seating Awaited" {
                        Text("RM \(exam.room)")
                            .font(.system(size: 11, weight: .bold))
                            .foregroundColor(.skedSlate)
                    }
                    Text(exam.timeSlot)
                        .font(.system(size: 9, weight: .medium))
                        .foregroundColor(.skedBlaze)
                }
            } else if entry.isSunday {
                // Sunday State
                VStack(alignment: .leading, spacing: 4) {
                    Text("SUNDAY.")
                        .font(.system(size: 11, weight: .black))
                        .foregroundColor(.skedBlaze)
                    Text("Enjoy your Sunday.")
                        .font(.system(size: 13, weight: .bold))
                        .foregroundColor(.skedChalk)
                    Text("You're clear.")
                        .font(.system(size: 10, weight: .medium))
                        .foregroundColor(.skedSlate)
                }
            } else if entry.classes.isEmpty {
                // Empty State
                VStack(alignment: .leading, spacing: 3) {
                    Text("Nothing today.")
                        .font(.system(size: 14, weight: .bold))
                        .foregroundColor(.skedChalk)
                    Text("You're clear.")
                        .font(.system(size: 11, weight: .medium))
                        .foregroundColor(.skedSlate)
                }
            } else if entry.allDone {
                // All Done State
                VStack(alignment: .leading, spacing: 3) {
                    Text("All done.")
                        .font(.system(size: 15, weight: .bold))
                        .foregroundColor(.skedChalk)
                    Text("\(entry.classes.count) classes completed.")
                        .font(.system(size: 10, weight: .medium))
                        .foregroundColor(.skedSlate)
                }
            } else if let live = entry.liveClass {
                // Live Class
                VStack(alignment: .leading, spacing: 3) {
                    HStack(spacing: 4) {
                        Circle()
                            .fill(Color.skedGreen)
                            .frame(width: 6, height: 6)
                        Text("NOW • \(live.shortType)")
                            .font(.system(size: 9, weight: .bold))
                            .foregroundColor(.skedGreen)
                    }
                    Text(live.courseCode)
                        .font(.system(size: 18, weight: .black, design: .rounded))
                        .foregroundColor(.skedChalk)
                    Text(live.room)
                        .font(.system(size: 11, weight: .bold))
                        .foregroundColor(.skedSlate)
                    Text(live.start)
                        .font(.system(size: 10, weight: .bold))
                        .foregroundColor(.skedBlaze)
                }
            } else if let next = entry.nextClass {
                // Next Class
                VStack(alignment: .leading, spacing: 3) {
                    HStack(spacing: 4) {
                        Circle()
                            .fill(Color.skedBlaze)
                            .frame(width: 6, height: 6)
                        Text("NEXT • \(next.shortType)")
                            .font(.system(size: 9, weight: .bold))
                            .foregroundColor(.skedBlaze)
                    }
                    Text(next.courseCode)
                        .font(.system(size: 18, weight: .black, design: .rounded))
                        .foregroundColor(.skedChalk)
                    Text(next.room)
                        .font(.system(size: 11, weight: .bold))
                        .foregroundColor(.skedSlate)
                    Text(next.start)
                        .font(.system(size: 10, weight: .bold))
                        .foregroundColor(.skedBlaze)
                }
            }

            Spacer()
        }
        .padding(12)
    }
}

// ── Medium Widget ─────────────────────────────────────────────────────────────
struct MediumWidgetView: View {
    let entry: Provider.Entry

    var headerSubtitle: String {
        if let exam = entry.exam {
            return "\(exam.examType) EXAM"
        } else if entry.isSunday {
            return "SUNDAY"
        } else if !entry.classes.isEmpty {
            return "\(entry.classes.count) classes"
        } else {
            return "Clear"
        }
    }

    var body: some View {
        VStack(spacing: 8) {
            // Header Bar
            HStack {
                Text("SKED.")
                    .font(.system(size: 14, weight: .black, design: .monospaced))
                    .foregroundColor(.skedBlaze)
                Spacer()
                Text(headerSubtitle)
                    .font(.system(size: 11, weight: .bold))
                    .foregroundColor(.skedSlate)
            }

            if let exam = entry.exam {
                // Exam Spotlight Mode
                HStack(spacing: 12) {
                    // Left Accent Bar + Exam Info
                    HStack(spacing: 10) {
                        RoundedRectangle(cornerRadius: 2)
                            .fill(Color.skedBlaze)
                            .frame(width: 3.5)

                        VStack(alignment: .leading, spacing: 3) {
                            HStack(spacing: 6) {
                                Circle().fill(Color.skedBlaze).frame(width: 6, height: 6)
                                Text(exam.isToday ? "EXAM TODAY." : "EXAM TOMORROW.")
                                    .font(.system(size: 10, weight: .bold))
                                    .foregroundColor(.skedBlaze)
                                Text("\(exam.examType) • \(exam.session.uppercased())")
                                    .font(.system(size: 9, weight: .bold))
                                    .foregroundColor(.skedSlate)
                            }
                            Text(exam.courseCode)
                                .font(.system(size: 18, weight: .black, design: .rounded))
                                .foregroundColor(.skedChalk)
                            Text(exam.courseTitle)
                                .font(.system(size: 10, weight: .medium))
                                .foregroundColor(.skedSlate)
                                .lineLimit(1)
                            Text(exam.timeSlot)
                                .font(.system(size: 11, weight: .bold))
                                .foregroundColor(.skedBlaze)
                        }
                    }
                    .padding(10)
                    .background(Color.skedSlabElevated)
                    .cornerRadius(8)

                    // Right Logistics Box
                    VStack(alignment: .leading, spacing: 6) {
                        if !exam.room.isEmpty && exam.room != "Seating Awaited" {
                            Text("ROOM")
                                .font(.system(size: 8, weight: .bold))
                                .foregroundColor(.skedSlate)
                            Text(exam.room)
                                .font(.system(size: 13, weight: .black))
                                .foregroundColor(.skedChalk)
                        }
                        if !exam.seatNo.isEmpty && exam.seatNo != "Awaited" {
                            Text("SEAT")
                                .font(.system(size: 8, weight: .bold))
                                .foregroundColor(.skedSlate)
                            Text(exam.seatNo)
                                .font(.system(size: 12, weight: .bold))
                                .foregroundColor(.skedGreen)
                        }
                        if !exam.reportingTime.isEmpty {
                            Text(exam.reportingTime)
                                .font(.system(size: 9, weight: .bold))
                                .foregroundColor(.skedBlaze)
                        }
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(8)
                    .background(Color.skedSlab)
                    .cornerRadius(6)
                }
            } else if entry.isSunday {
                // Sunday Relax Mode
                HStack {
                    VStack(alignment: .leading, spacing: 4) {
                        Text("SUNDAY RELAX.")
                            .font(.system(size: 12, weight: .black))
                            .foregroundColor(.skedBlaze)
                        Text("Enjoy your Sunday.")
                            .font(.system(size: 16, weight: .bold))
                            .foregroundColor(.skedChalk)
                        Text("No classes scheduled for today.")
                            .font(.system(size: 11, weight: .medium))
                            .foregroundColor(.skedSlate)
                    }
                    Spacer()
                }
                .padding(12)
                .frame(maxWidth: .infinity)
                .background(Color.skedSlabElevated)
                .cornerRadius(8)
            } else if entry.classes.isEmpty {
                // Empty State
                VStack(spacing: 4) {
                    Spacer()
                    Text("Nothing today.")
                        .font(.system(size: 16, weight: .bold))
                        .foregroundColor(.skedChalk)
                    Text("You're clear.")
                        .font(.system(size: 12, weight: .medium))
                        .foregroundColor(.skedSlate)
                    Spacer()
                }
                .frame(maxWidth: .infinity)
                .background(Color.skedSlab)
                .cornerRadius(8)
            } else {
                // Normal Class Day: Departure Board Layout
                HStack(spacing: 8) {
                    // Left Hero Card (Live or Next Class)
                    let spotlight = entry.liveClass ?? entry.nextClass ?? entry.classes.first!
                    let isLive = entry.liveClass != nil

                    HStack(spacing: 8) {
                        RoundedRectangle(cornerRadius: 2)
                            .fill(isLive ? Color.skedGreen : Color.skedBlaze)
                            .frame(width: 3.5)

                        VStack(alignment: .leading, spacing: 3) {
                            HStack(spacing: 4) {
                                Circle()
                                    .fill(isLive ? Color.skedGreen : Color.skedBlaze)
                                    .frame(width: 6, height: 6)
                                Text(isLive ? "NOW" : "NEXT")
                                    .font(.system(size: 9, weight: .black))
                                    .foregroundColor(isLive ? .skedGreen : .skedBlaze)
                                Text(spotlight.shortType)
                                    .font(.system(size: 9, weight: .bold))
                                    .foregroundColor(.skedSlate)
                            }
                            Text(spotlight.courseCode)
                                .font(.system(size: 17, weight: .black, design: .rounded))
                                .foregroundColor(.skedChalk)
                            Text(spotlight.timeRange)
                                .font(.system(size: 10, weight: .bold))
                                .foregroundColor(isLive ? .skedGreen : .skedBlaze)
                            Text(spotlight.room)
                                .font(.system(size: 10, weight: .bold))
                                .foregroundColor(.skedSlate)
                        }
                    }
                    .padding(8)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(Color.skedSlabElevated)
                    .cornerRadius(8)

                    // Right Departure List (shows classes including live/next)
                    VStack(spacing: 4) {
                        let classList = Array(entry.classes.prefix(3))
                        if classList.isEmpty {
                            VStack(spacing: 2) {
                                Spacer()
                                Text("All caught up")
                                    .font(.system(size: 11, weight: .bold))
                                    .foregroundColor(.skedSlate)
                                Spacer()
                            }
                        } else {
                            ForEach(classList) { item in
                                let isLive = item.id == entry.liveClass?.id
                                let isNext = item.id == entry.nextClass?.id
                                HStack {
                                    Circle()
                                        .fill(isLive ? Color.skedGreen : (isNext ? Color.skedBlaze : Color.skedSlate))
                                        .frame(width: 5, height: 5)
                                    Text(item.start)
                                        .font(.system(size: 10, weight: .bold, design: .monospaced))
                                        .foregroundColor(isLive ? .skedGreen : (isNext ? .skedChalk : .skedSlate))
                                        .frame(width: 36, alignment: .leading)
                                    Text(item.courseCode)
                                        .font(.system(size: 11, weight: .bold))
                                        .foregroundColor(isLive ? .skedGreen : (isNext ? .skedChalk : .skedSlate))
                                    Spacer()
                                    Text(item.room)
                                        .font(.system(size: 9, weight: .medium))
                                        .foregroundColor(.skedSlate)
                                        .padding(.horizontal, 4)
                                        .padding(.vertical, 1)
                                        .background(Color.skedRule)
                                        .cornerRadius(3)
                                }
                                .padding(.horizontal, 6)
                                .padding(.vertical, 4)
                                .background(isLive ? Color.skedSlabElevated : Color.skedSlab)
                                .cornerRadius(4)
                            }
                        }
                    }
                    .frame(maxWidth: .infinity)
                }
            }
        }
        .padding(12)
    }
}

// ── Widget Configuration ──────────────────────────────────────────────────────
struct TimetableWidget: Widget {
    let kind: String = "TimetableWidget"

    var body: some WidgetConfiguration {
        StaticConfiguration(kind: kind, provider: Provider()) { entry in
            TimetableWidgetEntryView(entry: entry)
        }
        .configurationDisplayName("Sked Timetable")
        .description("View your live classes, room numbers, and exam countdowns on your Home Screen.")
        .supportedFamilies([.systemSmall, .systemMedium])
        .contentMarginsDisabled()
    }
}
