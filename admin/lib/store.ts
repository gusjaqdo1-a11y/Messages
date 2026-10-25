import { get, put } from '@vercel/blob';

export type AdminData = {
  users: any[];
  senderIds: any[];
  providers: any[];
  messages: any[];
  broadcasts: any[];
  apiKeys: any[];
  webhooks: any[];
  audit: any[];
  settings: { maintenance: boolean; defaultCountry: string; defaultProvider: string; appMessaging: boolean };
};

export const defaults: AdminData = {
  users: [], senderIds: [], providers: [], messages: [], broadcasts: [], apiKeys: [], webhooks: [], audit: [],
  settings: { maintenance: false, defaultCountry: 'ET', defaultProvider: '', appMessaging: true },
};

const PATH = 'admin/messages-som.json';

export async function readData(): Promise<AdminData> {
  try {
    const result = await get(PATH, { access: 'private', useCache: false });
    if (!result) return structuredClone(defaults);
    const text = await new Response(result.stream).text();
    return { ...structuredClone(defaults), ...JSON.parse(text) };
  } catch {
    return structuredClone(defaults);
  }
}

export async function writeData(data: AdminData) {
  await put(PATH, JSON.stringify(data), { access: 'private', addRandomSuffix: false, allowOverwrite: true });
}

export function id(prefix: string) {
  return `${prefix}_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`;
}
