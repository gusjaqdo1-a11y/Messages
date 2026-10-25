import { NextResponse } from 'next/server';
import { cookies } from 'next/headers';
import crypto from 'node:crypto';
import { readData, writeData, id } from '../../../../lib/store';

async function authorized(){
  const c=(await cookies()).get('messages_admin')?.value; const secret=process.env.SESSION_SECRET;
  if(!c||!secret)return false; const [exp,sig]=c.split('.'); if(!exp||!sig||!/^[0-9]+$/.test(exp)||Number(exp)<Date.now())return false;
  const good=crypto.createHmac('sha256',secret).update(exp).digest('hex'); const a=Buffer.from(good),b=Buffer.from(sig); return a.length===b.length&&crypto.timingSafeEqual(a,b);
}

const API_BASE=process.env.APP_MESSAGES_API_BASE||'https://messages-som-api.vercel.app/api/v1';

export async function GET(){
  if(!(await authorized()))return NextResponse.json({error:'Unauthorized'},{status:401});
  const data=await readData();
  return NextResponse.json({ok:true,data:{senderIds:data.senderIds.filter((s:any)=>s.enabled!==false),apiKeys:data.apiKeys.filter((k:any)=>k.enabled!==false&&String(k.scope||'').includes('app_messages'))}});
}

export async function POST(req:Request){
  if(!(await authorized()))return NextResponse.json({error:'Unauthorized'},{status:401});
  const body=await req.json().catch(()=>({})); const action=String(body.action||'send'); const data=await readData();
  if(action==='generateKey'){
    const token=`msom_${crypto.randomBytes(32).toString('base64url')}`; const item={id:id('key'),name:String(body.name||'Messages SOM App'),scope:'app_messages:send',enabled:true,token,createdAt:new Date().toISOString(),updatedAt:new Date().toISOString()}; data.apiKeys.unshift(item); data.audit.unshift({id:id('audit'),action:'app_messages_api_key_created',details:item.name,createdAt:new Date().toISOString()}); await writeData(data); return NextResponse.json({ok:true,token});
  }
  const key=process.env.APP_API_KEY; if(!key)return NextResponse.json({ok:false,error:'APP_API_KEY is not configured on admin'},{status:500});
  const r=await fetch(`${API_BASE}/app-messages/send`,{method:'POST',headers:{authorization:`Bearer ${key}`,'content-type':'application/json'},body:JSON.stringify({sender_id:body.sender_id,to:body.to,message:body.message}),cache:'no-store'});
  const text=await r.text(); let json:any; try{json=JSON.parse(text)}catch{json={error:text.slice(0,500)}}; return NextResponse.json(json,{status:r.status});
}
