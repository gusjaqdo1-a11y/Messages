# Messages SOM

Native Android SMS and app-to-app messaging foundation.

## Included
- Modern dark native Android UI
- SMS inbox and compose flow
- Contacts picker
- Send/receive SMS receiver and notifications
- Default SMS role request
- Firebase Anonymous Auth + Firestore + FCM integration
- Authenticated app-to-app messaging API
- Firestore-backed conversations with read/delivered state
- FCM foreground/background notifications
- Firestore security rules template
- GitHub Actions unit-test + debug APK build and automatic release
- Custom Messages SOM app mark
- Modern inbox tabs for All, Links, and Messages
- Link-aware conversation previews
- Sender-ID-safe compose behavior

## Firebase setup
The Android app uses the Firebase project configured in `app/google-services.json` and authenticates app users with Firebase Anonymous Auth. Enable **Anonymous** under Firebase Authentication, plus Firestore and Cloud Messaging. The Firebase Admin credentials stay server-side and must never be committed to the Android app.

The public app API authenticates the Firebase ID token and forwards requests to the protected Firebase bridge. Set `MESSAGES_BRIDGE_API_KEY` in the deployed admin/Vercel environment. The bridge can also auto-enable the Firestore API when `FIREBASE_AUTO_ENABLE_FIRESTORE=true`.

## App identity
Each installed app receives a Firebase anonymous identity and a stable Messages SOM ID such as `MSOM-AB12CD34`. Phone numbers are optional for discovery; users can also search by Messages SOM ID.

## CI
Every push to `main` runs Android unit tests and builds a debug APK. Successful builds are published as GitHub Releases.

Never commit Firebase Admin service-account private keys or other server secrets.
