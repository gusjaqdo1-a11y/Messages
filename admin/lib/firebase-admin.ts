import { getApps, initializeApp, cert, type App } from 'firebase-admin/app';
import { getFirestore, type Firestore } from 'firebase-admin/firestore';
import { getMessaging, type Messaging } from 'firebase-admin/messaging';

let app: App | undefined;

export function firebaseApp(): App {
  if (app) return app;
  const existing = getApps()[0];
  if (existing) { app = existing; return existing; }
  const json = process.env.FIREBASE_SERVICE_ACCOUNT_JSON?.trim();
  if (json) {
    app = initializeApp({ credential: cert(JSON.parse(json)) });
    return app;
  }
  const projectId = process.env.FIREBASE_PROJECT_ID?.trim();
  const clientEmail = process.env.FIREBASE_CLIENT_EMAIL?.trim();
  const privateKey = process.env.FIREBASE_PRIVATE_KEY?.replace(/\\n/g, '\n');
  if (!projectId || !clientEmail || !privateKey) throw new Error('Firebase Admin credentials are not configured');
  app = initializeApp({ credential: cert({ projectId, clientEmail, privateKey }) });
  return app;
}

export function firebaseDb(): Firestore { return getFirestore(firebaseApp()); }
export function firebaseMessaging(): Messaging { return getMessaging(firebaseApp()); }
