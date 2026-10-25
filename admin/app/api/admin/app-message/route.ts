import {NextResponse} from 'next/server';
import {cookies} from 'next/headers';
import crypto from 'node:crypto';

async function authorized(){
  const c=(await cookies()).get('messages_admin')?.value;
  const secret=process.env.SESSION_SECRET;
  if(!c||!secret)return false;
  const [exp,sig]=c.split('.');
  if(!exp||!sig||!/^[0-9]+$/.test(exp)||Number(exp)<Date.now())return false;
  const good=crypto.createHmac('sha256',secret).update(exp).digest('hex');
  const a=Buffer.from(good),b=Buffer.from(sig);
  return a.length===b.length&&crypto.timingSafeEqual(a,b);
}

export async function POST(req:Request){
  if(!(await authorized()))return NextResponse.json({ok:false,error:'Unauthorized'},{status:401});
  const body=await req.json().catch(()=>({}));
  const base=process.env.MESSAGES_SOM_API_BASE||'https://messages-som-api.vercel.app/api/v1';
  const key=process.env.APP_API_KEY;
  if(!key)return NextResponse.json({ok:false,error:'APP_API_KEY is not configured on admin'},{status:503});
  try{
    const r=await fetch(`${base}/app-messages/send`,{method:'POST',headers:{authorization:`Bearer ${key}`,'content-type':'application/json'},body:JSON.stringify(body),cache:'no-store'});
    const text=await r.text();let json:any;try{json=JSON.parse(text)}catch{json={ok:false,error:text.slice(0,500)}}
    return NextResponse.json(json,{status:r.status});
  }catch(e:any){return NextResponse.json({ok:false,error:e?.message||'API request failed'},{status:502});}
}
