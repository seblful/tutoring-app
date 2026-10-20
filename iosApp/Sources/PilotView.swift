import SwiftUI

struct PilotView: View {
    @StateObject private var model = PilotModel()
    @State private var name = ""
    @State private var start = Date().addingTimeInterval(3_600)
    @FocusState private var editingName: Bool

    var body: some View {
        NavigationStack {
            Form {
                Section("New student") {
                    TextField("Name", text: $name)
                        .focused($editingName)
                        .accessibilityIdentifier("student-name")
                    Button("Add student") {
                        editingName = false
                        model.addStudent(name)
                        name = ""
                    }
                    .disabled(model.busy || name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                    .accessibilityIdentifier("add-student")
                }

                Section {
                    Picker("Student", selection: $model.selectedStudentID) {
                        Text("Select a student").tag(Int64(0))
                        ForEach(model.students) { student in
                            Text(student.name).tag(student.id)
                        }
                    }
                    DatePicker("Starts", selection: $start)
                    Button("Book 60-minute lesson") {
                        model.book(at: start)
                    }
                    .disabled(model.busy || model.selectedStudentID == 0)
                    .accessibilityIdentifier("book-lesson")
                } header: {
                    Text("Book a lesson")
                } footer: {
                    Text("Math · 30 USD per hour")
                }

                if let message = model.errorMessage {
                    Section {
                        Text(message)
                            .foregroundStyle(.red)
                            .accessibilityIdentifier("error-message")
                    }
                }

                Section("Students") {
                    ForEach(model.students) { student in
                        Text(student.name)
                            .accessibilityIdentifier("student-\(student.id)")
                    }
                }

                Section("Lessons") {
                    ForEach(model.lessons) { lesson in
                        VStack(alignment: .leading) {
                            Text(lesson.studentName)
                            Text(lesson.date, format: .dateTime.day().month().hour().minute())
                                .foregroundStyle(.secondary)
                        }
                        .accessibilityElement(children: .combine)
                        .accessibilityIdentifier("lesson-\(lesson.id)")
                    }
                }
            }
            .navigationTitle("Klas Pilot")
            .overlay {
                if model.busy { ProgressView() }
            }
            .task { model.load() }
        }
    }
}
