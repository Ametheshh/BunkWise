# BunkWise (integrated mini-project)

Sign in with your institutional email, then pick one of three tools:

1. **Attendance**: NMAMIT attendance (overall %, per-subject %, classes conducted/attended, daily history,
   subjects below 75%, classes needed to reach 75%).
2. **CGPA**: SGPA per semester, cumulative CGPA and a target planner.
3. **Document Vault**: AES-256-GCM encrypted marksheets, IDs and certificates.

There is no PIN screen. The institutional login is the front door, and Logout is at the top right of the
home screen and the attendance dashboard.

## Open in Android Studio
1. Open the folder that contains `settings.gradle.kts`.
2. Let Gradle sync finish, then Build > Rebuild Project.
3. **Uninstall any older copy of the app from the phone/emulator once**, then Run. (The old copy has a PIN
   and an old database layout.)

## Log in
- Email: `<usn>@nmamit.in`, for example `nnm23cb007@nmamit.in`
- Password: your USN, for example `nnm23cb007` (any letter case until you change it)

USNs in the data start with either NNM (7 students) or NMM (68 students). Login accepts **either spelling**
for every student, so `nnm23cb007@nmamit.in` and `nmm23cb007@nmamit.in` both open the same account. The
dashboard shows the USN exactly as it is stored in the data file.

## What is separate per student
CGPA semesters and vault documents belong to the student who is logged in (database rows are tagged with
the student id, and each student's encrypted files live in their own folder). Another student logging in on
the same phone cannot see them. Data saved by earlier versions of the app is not shown any more.

## Security
- Passwords: PBKDF2-HMAC-SHA256, 100,000 iterations, random salt. 5 wrong attempts lock the account for 15 min.
- The session lives in memory only: it ends on logout, after 15 idle minutes, or when the app is killed.
- Screens block screenshots, backups are off, all queries are parameterised.
- CGPA and vault data are AES-GCM encrypted with a key held in the Android Keystore.
- minSdk is 26 (Android 8.0) because PBKDF2-SHA256 needs it.

## Important limitation
The attendance database is packaged inside the APK (synthetic data). The app only shows each student their
own records, but anyone who unpacks the APK or has a compromised phone could read the whole bundled database.
For real use, put student data and authentication on a server and enforce the student id there. Never ship
real student data inside an APK.

## Data updates
The attendance database is a prebuilt snapshot copied to private storage on first launch. To refresh it,
rebuild `app/src/main/assets/attendance.db` and uninstall/reinstall the app.

## GitHub hygiene
Do not commit `local.properties`, `.gradle/`, build outputs, signing keys or real student data.
