import { get, put } from '@vercel/blob';
import crypto from 'node:crypto';

export type Data = {
  users:any[]; senderIds:any[]; providers:any[]; messages:any[]; broadcasts:any[]; apiKeys:any[]; webhooks:any[]; audit:any[];
  settings:{maintenance:boolean;defaultCountry:string;defaultProvider:string;appMessaging:boolean};
};

const PATH='admin/messages-som.json';
export const defaults:Data={users:[],senderIds:[],providers:[],messages:[],broadcasts:[],apiKeys:[],webhooks:[],audit:[],settings:{maintenance:false,defaultCountry:'ET',defaultProvider:'',appMessaging:true}};

export async function readData():Promise<Data>{
  try{const r=await get(PATH,{access:'private',useCache:false}); if(!r)return structuredClone(defaults); const t=await new Response(r.stream).text(); return {...structuredClone(defaults),...JSON.parse(t)};}catch{return structuredClone(defaults);}
}
export async function writeData(d:Data){await put(PATH,JSON.stringify(d),{access:'private',addRandomSuffix:false,allowOverwrite:true});}
export function newId(prefix:string){return `${prefix}_${Date.now()}_${crypto.randomBytes(4).toString('hex')}`;}
export function normalizeNumber(v:string){const s=String(v||'').trim().replace(/[\s()-]/g,''); return s;}
export function countryFromNumber(to:string){const n=normalizeNumber(to); const m:[[string,string]]|any=[['ET','251'],['SO','252'],['KE','254'],['UG','256'],['TZ','255'],['DJ','253'],['SD','249'],['RW','250'],['NG','234'],['GH','233'],['ZA','27'],['GB','44'],['DE','49'],['SE','46'],['US','1'],['CA','1']]; return m.find((x:any)=>n.startsWith('+'+x[1])||n.startsWith(x[1]))?.[0]||'';}
export function hasScope(k:any,scope:string){if(k?.scope==='*')return true; const s=Array.isArray(k?.scope)?k.scope:String(k?.scope||'').split(',').map((x:string)=>x.trim()); return s.includes(scope)||s.includes('messages:*')||s.includes('admin:*');}
export function authorizedKey(req:Request){const h=req.headers.get('authorization')||''; return h.startsWith('Bearer ')?h.slice(7).trim():req.headers.get('x-api-key')||'';}

async function callProvider(provider:any,sender:any,to:string,text:string){
  if(!provider?.enabled)throw new Error('Provider is disabled');
  if(provider.type==='mock')return {providerMessageId:`mock_${Date.now()}`,raw:{mock:true}};
  if(!provider.baseUrl)throw new Error('Provider API base URL is missing');
  const url=new URL(provider.sendPath||'/sms/send',provider.baseUrl).toString();
  const method=String(provider.method||'POST').toUpperCase();
  const headers:any={'content-type':'application/json',...(provider.headers||{})};
  const auth=String(provider.authType||'bearer').toLowerCase();
  if(provider.apiKey&&auth==='bearer')headers.authorization=`Bearer ${provider.apiKey}`;
  if(provider.apiKey&&auth==='x-api-key')headers['x-api-key']=provider.apiKey;
  if(provider.apiKey&&auth==='basic')headers.authorization=`Basic ${Buffer.from(`${provider.apiKey}:${provider.apiSecret||''}`).toString('base64')}`;
  const bodyTemplate=provider.bodyTemplate||'';
  const values:any={'{{sender_id}}':sender.providerSenderId||sender.name,'{{to}}':to,'{{message}}':text,'{{text}}':text};
  let payload:any={};
  if(bodyTemplate){let b=bodyTemplate; for(const [k,v] of Object.entries(values))b=b.split(k).join(String(v)); try{payload=JSON.parse(b);}catch{throw new Error('Provider bodyTemplate is not valid JSON');}}
  else {payload={}; payload[provider.senderField||'sender_id']=sender.providerSenderId||sender.name; payload[provider.toField||'to']=to; payload[provider.messageField||'message']=text;}
  const r=await fetch(url,{method,headers,body:method==='GET'?undefined:JSON.stringify(payload),cache:'no-store'});
  const raw=await r.text(); let json:any; try{json=JSON.parse(raw)}catch{json={raw}};
  if(!r.ok)throw new Error(`Provider HTTP ${r.status}: ${raw.slice(0,300)}`);
  const id=json?.messageId||json?.message_id||json?.id||json?.data?.messageId||json?.data?.message_id||json?.data?.id||`provider_${Date.now()}`;
  return {providerMessageId:String(id),raw:json};
}

export async function sendSms(input:{senderId:string;to:string;text:string;userId?:string;source?:string}){
  const data=await readData();
  if(data.settings.maintenance)throw new Error('SMS sending is temporarily disabled');
  const to=normalizeNumber(input.to); const text=String(input.text||'').trim();
  if(!/^\+?[1-9]\d{6,14}$/.test(to))throw new Error('Invalid international phone number');
  if(!text)throw new Error('Message text is required');
  if(text.length>1600)throw new Error('Message is too long (maximum 1600 characters)');
  const sender=data.senderIds.find((s:any)=>s.enabled!==false && (s.id===input.senderId || s.providerSenderId===input.senderId || s.name===input.senderId));
  if(!sender)throw new Error('Sender ID not found or disabled');
  const country=countryFromNumber(to); const allowed=Array.isArray(sender.countries)?sender.countries.map((x:string)=>x.toUpperCase()):[];
  if(allowed.length&&country&&!allowed.includes(country))throw new Error(`Sender ID is not enabled for ${country}`);
  const today=new Date().toISOString().slice(0,10); const month=new Date().toISOString().slice(0,7);
  const sent=data.messages.filter((m:any)=>m.senderId===sender.id&&(m.status==='sent'||m.status==='queued')&&(m.createdAt||'').startsWith(today)).length;
  const monthCount=data.messages.filter((m:any)=>m.senderId===sender.id&&(m.status==='sent'||m.status==='queued')&&(m.createdAt||'').startsWith(month)).length;
  if(Number(sender.dailyLimit)>0&&sent>=Number(sender.dailyLimit))throw new Error('Sender ID daily limit reached');
  if(Number(sender.monthlyLimit)>0&&monthCount>=Number(sender.monthlyLimit))throw new Error('Sender ID monthly limit reached');
  const provider=data.providers.find((p:any)=>p.id===sender.provider||p.name===sender.provider) || data.providers.filter((p:any)=>p.enabled!==false).sort((a:any,b:any)=>Number(a.priority||99)-Number(b.priority||99))[0];
  if(!provider)throw new Error('No SMS provider configured');
  const msg:any={id:newId('msg'),userId:input.userId||'api',senderId:sender.id,senderName:sender.providerSenderId||sender.name,to,text,status:'queued',provider:provider.name||provider.id,createdAt:new Date().toISOString(),updatedAt:new Date().toISOString(),source:input.source||'api'};
  data.messages.unshift(msg); await writeData(data);
  try{
    const result=await callProvider(provider,sender,to,text); msg.status='sent'; msg.providerMessageId=result.providerMessageId; msg.providerResponse=result.raw; msg.updatedAt=new Date().toISOString();
  }catch(e:any){msg.status='failed';msg.error=e?.message||'Provider request failed';msg.updatedAt=new Date().toISOString();}
  const fresh=await readData(); const idx=fresh.messages.findIndex((x:any)=>x.id===msg.id); if(idx>=0)fresh.messages[idx]={...fresh.messages[idx],...msg}; else fresh.messages.unshift(msg); await writeData(fresh);
  return msg;
}
