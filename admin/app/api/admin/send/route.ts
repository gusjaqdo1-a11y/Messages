import { NextResponse } from 'next/server';
import { cookies } from 'next/headers';
import crypto from 'node:crypto';
import { sendAdminSms } from '../../../../lib/sms';

async function authorized(){const c=(await cookies()).get('messages_admin')?.value;const secret=process.env.SESSION_SECRET;if(!c||!secret)return false;const [exp,sig]=c.split('.');if(!exp||!sig||!/^\d+$/.test(exp)||Number(exp)<Date.now())return false;const good=crypto.createHmac('sha256',secret).update(exp).digest('hex');const a=Buffer.from(good),b=Buffer.from(sig);return a.length===b.length&&crypto.timingSafeEqual(a,b)}
export async function POST(req:Request){if(!(await authorized()))return NextResponse.json({error:'Unauthorized'},{status:401});try{const b=await req.json();if(!b.senderId||!b.to||!b.text)return NextResponse.json({error:'Sender ID, recipient and message are required'},{status:400});const msg=await sendAdminSms({senderId:String(b.senderId),to:String(b.to),text:String(b.text)});return NextResponse.json({ok:msg.status==='sent',data:msg},{status:msg.status==='sent'?200:502})}catch(e:any){return NextResponse.json({ok:false,error:e?.message||'Send failed'},{status:400})}}
