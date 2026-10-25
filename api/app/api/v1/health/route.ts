import {NextResponse} from 'next/server';
import {readData} from '../../../../lib/sms';
export const dynamic='force-dynamic';
export async function GET(){const d=await readData();const enabledProviders=d.providers.filter((p:any)=>p.enabled!==false).length;const enabledSenders=d.senderIds.filter((s:any)=>s.enabled!==false).length;return NextResponse.json({ok:true,service:'messages-som-api',version:'v1',status:'online',capabilities:{sms:enabledProviders>0,providers:enabledProviders,senderIds:enabledSenders,appMessaging:Boolean(d.settings?.appMessaging)},timestamp:new Date().toISOString()},{headers:{'cache-control':'no-store'}})}
