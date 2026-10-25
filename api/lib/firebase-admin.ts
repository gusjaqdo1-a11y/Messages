import { getApps, initializeApp, cert, App } from 'firebase-admin/app';
import { getFirestore, Firestore } from 'firebase-admin/firestore';
import { getMessaging, Messaging } from 'firebase-admin/messaging';

let app: App | undefined;

function firebaseApp(): App {
  if (app) return app;
  const existing = getApps()[0];
  if (existing) { app = existing; return existing; }
  const json = process.env.FIREBASE_SERVICE_ACCOUNT_JSON;
  if (json) {
    app = initializeApp({ credential: cert(JSON.parse(json)) });
    return app;
  }
  const projectId = process.env.FIREBASE_PROJECT_ID;
  const clientEmail = process.env.FIREBASE_CLIENT_EMAIL;
  const privateKey = process.env.FIREBASE_PRIVATE_KEY?.replace(/\\n/g, '\n');
  if (!projectId || !clientEmail || !privateKey) throw new Error('Firebase Admin credentials are not configured');
  app = initializeApp({ credential: cert({ projectId, clientEmail, privateKey }) });
  return app;
}

export function firebaseDb(): Firestore { return getFirestore(firebaseApp()); }
export function firebaseMessaging(): Messaging { return getMessaging(firebaseApp()); }
