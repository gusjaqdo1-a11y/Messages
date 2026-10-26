import {NextResponse} from 'next/server';
import crypto from 'crypto';
import {firebaseDb} from '../../../../../lib/firebase-admin';
import {normalizeNumber} from '../../../../../lib/sms';
function uidOf(id:string){return 'app:'+crypto.createHash('sha256').update(id).digest('hex').slice(0,40)}
export async function POST(req:Request){try{
 const b=await req.json().catch(()=>({})); const installation=String(b.installation_id||'').trim(), q=String(b.query||'').trim();
 if(installation.length<32||q.length<3)return NextResponse.json({ok:false,error:'Invalid search request'},{status:400});
 const me=uidOf(installation), db=firebaseDb(); let snap;
 const normalized=normalizeNumber(q);
 if(normalized) snap=await db.collection('profiles').where('phoneNumber','==',normalized).limit(10).get();
 else {const id=q.toUpperCase(); if(!id.startsWith('MSOM-'))return NextResponse.json({ok:true,data:[]}); snap=await db.collection('profiles').where('messagesId','==',id).limit(10).get()}
 const data=snap.docs.filter(d=>d.id!==me&&d.data().enabled!==false).map(d=>{const x=d.data();return {uid:d.id,messagesId:x.messagesId||'',displayName:x.displayName||'Messages SOM user',phoneNumber:x.phoneNumber||'',photoUrl:x.photoUrl||'',fcmToken:'',enabled:x.enabled!==false,createdAt:x.createdAt||0,updatedAt:x.updatedAt||0,allowReplies:x.allowReplies!==false}});
 return NextResponse.json({ok:true,data});
}catch(e:any){return NextResponse.json({ok:false,error:e?.message||'Search failed'},{status:500})}}
