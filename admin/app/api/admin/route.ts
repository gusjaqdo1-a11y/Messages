import { NextResponse } from 'next/server';
import { cookies } from 'next/headers';
import crypto from 'node:crypto';

const defaults = {users:[],senderIds:[],providers:[],messages:[],audit:[],settings:{maintenance:false,defaultCountry:'ET'}};
async function authorized(){
  const c=(await cookies()).get('messages_admin')?.value; const secret=process.env.SESSION_SECRET;
  if(!c||!secret) return false; const [exp,sig]=c.split('.'); if(!exp||!sig||Number(exp)<Date.now()) return false;
  const good=crypto.createHmac('sha256',secret).update(exp).digest('hex'); return crypto.timingSafeEqual(Buffer.from(good),Buffer.from(sig));
}
export async function GET(){if(!(await authorized()))return NextResponse.json({error:'Unauthorized'},{status:401}); return NextResponse.json({ok:true,data:defaults});}
export async function POST(req:Request){if(!(await authorized()))return NextResponse.json({error:'Unauthorized'},{status:401}); const body=await req.json().catch(()=>({})); return NextResponse.json({ok:true,action:body.action??'noop',item:body.item??null});}