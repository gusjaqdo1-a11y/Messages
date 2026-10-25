import { NextResponse } from 'next/server';
import crypto from 'node:crypto';

export async function POST(req: Request) {
  const { password } = await req.json().catch(() => ({}));
  const expected = process.env.ADMIN_PASSWORD;
  const secret = process.env.SESSION_SECRET;
  if (!expected || !secret) return NextResponse.json({error:'Admin authentication is not configured'}, {status:500});
  const supplied = typeof password === 'string' ? password : '';
  const a = crypto.createHash('sha256').update(supplied).digest();
  const b = crypto.createHash('sha256').update(expected).digest();
  if (!crypto.timingSafeEqual(a,b)) return NextResponse.json({error:'Invalid password'}, {status:401});
  const exp = Date.now() + 1000*60*60*24*7;
  const payload = String(exp);
  const sig = crypto.createHmac('sha256', secret).update(payload).digest('hex');
  const res = NextResponse.json({ok:true});
  res.cookies.set('messages_admin', `${payload}.${sig}`, {httpOnly:true,secure:true,sameSite:'lax',path:'/',maxAge:60*60*24*7});
  return res;
}

export async function DELETE() {
  const res = NextResponse.json({ok:true});
  res.cookies.set('messages_admin','',{httpOnly:true,secure:true,sameSite:'lax',path:'/',maxAge:0});
  return res;
}