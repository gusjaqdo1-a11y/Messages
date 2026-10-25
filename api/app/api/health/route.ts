import {NextResponse} from 'next/server';
export const dynamic='force-dynamic';
export async function GET(){return NextResponse.json({ok:true,service:'messages-som-api',version:'1.0',status:'online',timestamp:new Date().toISOString()},{headers:{'cache-control':'no-store'}})}
