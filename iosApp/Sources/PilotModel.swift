import Combine
import Foundation
import KlasShared

struct StudentRow: Identifiable {
    let id: Int64
    let name: String
}

struct LessonRow: Identifiable {
    let id: Int64
    let studentName: String
    let date: Date
}

@MainActor
final class PilotModel: ObservableObject {
    @Published private(set) var students: [StudentRow] = []
    @Published private(set) var lessons: [LessonRow] = []
    @Published private(set) var busy = false
    @Published private(set) var errorMessage: String?
    @Published var selectedStudentID: Int64 = 0

    private let pilot: IosPilot?

    init() {
        let arguments = ProcessInfo.processInfo.arguments
        let key = "--database"
        let database: String
        if let index = arguments.firstIndex(of: key), index + 1 < arguments.count {
            database = arguments[index + 1]
        } else {
            database = "klas-pilot.db"
        }
        do {
            pilot = try IosPilot(databaseName: database)
        } catch {
            pilot = nil
            errorMessage = error.localizedDescription
        }
    }

    func load() {
        guard let pilot, !busy else { return }
        busy = true
        pilot.load(completion: completion)
    }

    func addStudent(_ name: String) {
        guard let pilot, !busy else { return }
        busy = true
        pilot.addStudent(name: name, completion: completion)
    }

    func book(at date: Date) {
        guard let pilot, !busy, selectedStudentID != 0 else { return }
        busy = true
        pilot.book(
            studentId: selectedStudentID,
            startsAtMillis: Int64(date.timeIntervalSince1970 * 1_000),
            completion: completion
        )
    }

    private var completion: (PilotSnapshot?, String?) -> Void {
        { [weak self] snapshot, failure in
            Task { @MainActor [weak self] in
                guard let self else { return }
                self.busy = false
                self.errorMessage = failure
                guard let snapshot else { return }
                self.students = snapshot.students.map { StudentRow(id: $0.id, name: $0.name) }
                self.lessons = snapshot.lessons.map {
                    LessonRow(
                        id: $0.id,
                        studentName: $0.studentName,
                        date: Date(timeIntervalSince1970: Double($0.startsAtMillis) / 1_000)
                    )
                }
                if !self.students.contains(where: { $0.id == self.selectedStudentID }) {
                    self.selectedStudentID = self.students.first?.id ?? 0
                }
            }
        }
    }
}
