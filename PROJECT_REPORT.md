# BunkWise project report

BunkWise brings attendance, assessment marks, CGPA planning, and a private document vault into one Android app. It provides a student view and a teacher dashboard using a local SQLite dataset. The starting database contains 75 students, eight subjects, and 24,563 attendance records.

## Design and implementation

Student sessions use role-scoped data queries. Teachers load an enrolled roster by assigned subject, date, and timetable slot. Practical classes additionally use the selected lab batch. A teacher can save attendance or correct an existing record and enter or correct IA1, IA2, Assignment, Lab, and Semester End marks. Each changed attendance or mark record produces an audit entry showing the account, subject, student, previous value, new value, and timestamp. Students read their own entries on the Marks screen.

The SQLite migration installs teacher accounts, assignments, marks, audit tables, and demo term dates without replacing existing student data. The CGPA and vault database checks both tables for missing student ownership columns before use. Passwords are hashed with salted PBKDF2. The vault uses AES-GCM with keys held by Android Keystore. The app locks accounts after five failed attempts and ends sessions after 15 idle minutes.

## Test matrix

| Test | Expected result | Status |
| --- | --- | --- |
| Fresh and upgraded database | Original roster and 24,563 attendance records preserved, four demo teachers seeded, repeated upgrade safe | Verified with host SQLite |
| Teacher demo passwords | Each fixture matches its PBKDF2 salt and hash | Verified with host Python |
| Invalid marks and duplicate identities | SQLite rejects scores over their maximum, missing enrollments, and duplicate marks keys | Verified with host SQLite |
| Gradle sync and debug APK | Gradle runs on JDK 21 and assembles the debug APK | Pending Android build environment |
| Student sign-in | NNM and NMM aliases open the same student account | Pending device run |
| Teacher sign-in and scope | Staff login opens the dashboard with assigned subjects only | Pending device run |
| Attendance and audit | Save and correct a class, reload the updated roster, verify before and after values | Instrumented test included, pending device run |
| Marks and audit | Save and correct a mark. The student sees their value and the teacher sees the audit entry | Instrumented repository test included, UI pending |
| Practical batch | B1 and B2 show their own rosters | Pending device run |
| Password change and lockout | Old password fails after a change, fifth failed attempt starts the lockout | Pending device run |
| Vault and CGPA | Upgraded schema opens cleanly, and one student's data is isolated from another's | Pending device run |
| Screenshots | Capture student home, teacher dashboard, attendance roster, and student marks from a running demo | Pending device run |

## Viva sheet

| Question | Answer |
| --- | --- |
| Why use SQLite? | It supports offline storage and fast local queries for attendance, enrollments, and marks. |
| How is teacher access restricted? | The active session identifies the teacher. The repository checks the subject assignment before loading or writing records and checks enrollment for individual marks. |
| How do corrections stay traceable? | The main write and its audit entry are in the same transaction. The audit entry records the teacher, old value, new value, and timestamp. |
| What happens if data changes while a form is open? | The save transaction checks the current values against the loaded ones. If they differ, the teacher reloads before saving. |
| How are passwords stored? | Each password is represented by a salted PBKDF2-HMAC-SHA256 hash. |
| How does the document vault differ from the attendance database? | Vault documents and metadata are encrypted with AES-GCM using keys managed by Android Keystore. The bundled attendance data lives in the app's private SQLite database. |
| Can two devices share teacher updates? | Each installation currently uses its own local database. Sharing updates across devices would require a backend and synchronization. |
| Why use JDK 21 for the build? | Gradle 8.13 supports running on JDK 21. Java 25 requires Gradle 9.1 or later. |

## CIE import and system administration

The app includes 150 imported CIE results across two subjects and 450 simulated results across the six other subjects. Marks are matched by USN. The administrator can view aggregate counts, teacher assignments, and the latest 100 teacher audit entries. The administrator and teacher sign-in flows and the CIE screens still require device verification.
