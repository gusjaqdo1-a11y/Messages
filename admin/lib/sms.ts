import { readData, writeData } from './store';
import crypto from 'node:crypto';

function normalizeNumber(v:string){return String(v||'').trim().replace(/[\s()-]/g,'');}
function countryFromNumber(to:string){const n=normalizeNumber(to);const m:any=[['ET','251'],['SO','252'],['KE','254'],['UG','256'],['TZ','255'],['DJ','253'],['SD','249'],['RW','250'],['NG','234'],['GH','233'],['ZA','27'],['GB','44'],['DE','49'],['SE','46'],['US','1'],['CA','1']];return m.find((x:any)=>n.startsWith('+'+x[1])||n.startsWith(x[1]))?.[0]||'';}

async function providerSend(provider:any,sender:any,to:string,text:string){
 if(!provider?.enabled)throw new Error('Provider is disabled');
 if(provider.type==='mock')return {providerMessageId:`mock_${Date.now()}`};
 if(!provider.baseUrl)throw new Error('Provider API base URL is missing');
 const url=new URL(provider.sendPath||'/sms/send',provider.baseUrl).toString();
 const headers:any={'content-type':'application/json',...(provider.headers||{})};
 const auth=String(provider.authType||'bearer').toLowerCase();
 if(provider.apiKey&&auth==='bearer')headers.authorization=`Bearer ${provider.apiKey}`;
 if(provider.apiKey&&auth==='x-api-key')headers['x-api-key']=provider.apiKey;
 if(provider.apiKey&&auth==='basic')headers.authorization=`Basic ${Buffer.from(`${provider.apiKey}:${provider.apiSecret||''}`).toString('base64')}`;
 const repl:any={'{{sender_id}}':sender.providerSenderId||sender.name,'{{to}}':to,'{{message}}':text,'{{text}}':text};
 let payload:any={};
 if(provider.bodyTemplate){let raw=String(provider.bodyTemplate);for(const [k,v] of Object.entries(repl))raw=raw.split(k).join(String(v));payload=JSON.parse(raw);}else{payload[provider.senderField||'sender_id']=sender.providerSenderId||sender.name;payload[provider.toField||'to']=to;payload[provider.messageField||'message']=text;}
 const method=String(provider.method||'POST').toUpperCase();
 const r=await fetch(url,{method,headers,body:method==='GET'?undefined:JSON.stringify(payload),cache:'no-store'});const raw=await r.text();let json:any;try{json=JSON.parse(raw)}catch{json={raw}};if(!r.ok)throw new Error(`Provider HTTP ${r.status}: ${raw.slice(0,300)}`);
 return {providerMessageId:String(json?.messageId||json?.message_id||json?.id||json?.data?.id||`provider_${Date.now()}`),response:json};
}

export async function sendAdminSms(input:{senderId:string;to:string;text:string}){
 const data=await readData();const to=normalizeNumber(input.to);const text=String(input.text||'').trim();
 if(!/^\+?[1-9]\d{6,14}$/.test(to))throw new Error('Use an international number, e.g. +2519XXXXXXXX');if(!text)throw new Error('Message text is required');
 const sender=data.senderIds.find((s:any)=>s.enabled!==false&&(s.id===input.senderId||s.providerSenderId===input.senderId||s.name===input.senderId));if(!sender)throw new Error('Sender ID not found or disabled');
 const country=countryFromNumber(to);const allowed=Array.isArray(sender.countries)?sender.countries.map((x:string)=>x.toUpperCase()):[];if(allowed.length&&country&&!allowed.includes(country))throw new Error(`Sender ID is not enabled for ${country}`);
 const provider=data.providers.find((p:any)=>p.id===sender.provider||p.name===sender.provider)||data.providers.filter((p:any)=>p.enabled!==false).sort((a:any,b:any)=>Number(a.priority||99)-Number(b.priority||99))[0];if(!provider)throw new Error('No SMS provider configured');
 const now=new Date().toISOString();const msg:any={id:`msg_${Date.now()}_${crypto.randomBytes(3).toString('hex')}`,senderId:sender.id,senderName:sender.providerSenderId||sender.name,to,text,status:'queued',provider:provider.name||provider.id,createdAt:now,updatedAt:now,source:'admin'};data.messages.unshift(msg);await writeData(data);
 try{const r=await providerSend(provider,sender,to,text);msg.status='sent';msg.providerMessageId=r.providerMessageId;msg.providerResponse=r.response;}catch(e:any){msg.status='failed';msg.error=e?.message||'Provider request failed';}
 msg.updatedAt=new Date().toISOString();const fresh=await readData();const i=fresh.messages.findIndex((x:any)=>x.id===msg.id);if(i>=0)fresh.messages[i]={...fresh.messages[i],...msg};else fresh.messages.unshift(msg);await writeData(fresh);return msg;
}
